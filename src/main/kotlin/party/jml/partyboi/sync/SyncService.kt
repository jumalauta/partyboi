package party.jml.partyboi.sync

import arrow.core.flatten
import arrow.core.raise.either
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.mindrot.jbcrypt.BCrypt
import org.postgresql.util.PSQLException
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.*
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.catchResult
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.ValidURI
import party.jml.partyboi.validation.Validateable
import java.net.URI
import kotlin.io.path.fileSize

enum class SyncedTable(
    val tableName: String,
    val exceptionResolver: ExceptionResolver? = null
) {
    Users("appuser", { error, row ->
        if (error is PSQLException) {
            if (error.message?.contains("appuser_name_key") == true) {
                row["name"]?.jsonPrimitive?.content?.let { name ->
                    row.plus(mapOf("name" to JsonPrimitive("$name-${randomShortId()}")))
                }
            } else if (error.message?.contains("appuser_email_key") == true) {
                row.plus(mapOf("email" to JsonNull))
            } else null
        } else null
    }),
    Compos("compo"),
    Files("file"),
    Entries("entry"),
    EntryFiles("entry_file"),
    Previews("preview"),
    Events("event"),
    Messages("message"),
    Properties("property"),
    SlideSets("slideset"),
    InfoScreens("screen"),
    Triggers("trigger"),
    Votes("vote"),
    VoteKeys("votekey")
}

class SyncService(app: AppServices) : Service(app) {
    val expectedApiKey = property<String?>("apiKey", null)
    val remoteInstance = property<RemoteInstance?>("remoteInstance", null, private = true)

    private val db = DbSyncService(app)
    private val syncLog = SyncLogRepository(app)

    private val client = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = false
                    isLenient = false
                }
            )
        }
    }

    suspend fun generateNewToken(): AppResult<String> = either {
        val token = randomToken()
        expectedApiKey.set(hashToken(token)).bind()
        token
    }

    suspend fun isValidToken(token: String): AppResult<Boolean> =
        expectedApiKey.get().map { hashedToken ->
            BCrypt.checkpw(token, hashedToken)
        }

    suspend fun getTable(table: SyncedTable) = db.getTable(table.tableName)
    suspend fun putTable(table: Table) =
        db.putTable(
            table = table,
            exceptionResolver = SyncedTable.entries.find { it.tableName == table.table }?.exceptionResolver
        )

    suspend fun run() = either {
        val instance = remoteInstance.get().bind()
        if (instance == null) {
            raise(InvalidConfiguration())
        }
        downloadAndMergeTables(instance).bind()
        downloadMissingFiles(instance).bind()
    }

    suspend fun getLog() = syncLog.getAll()

    private suspend fun downloadAndMergeTables(instance: RemoteInstance) = either {
        SyncedTable.entries.forEach { table ->
            val importedData = downloadTable(instance, table).bind()
            putTable(importedData).bind()
            log.info("Table ${table.tableName} synced successfully (${importedData.data.size} entries)")
        }
    }

    private suspend fun downloadTable(instance: RemoteInstance, table: SyncedTable): AppResult<Table> =
        syncLog.use(TableSyncId(table.tableName)) {
            catchResult {
                client.get("${instance.address}/sync/table/${table.name.lowercase()}") {
                    accept(ContentType.Application.Json)
                    bearerAuth(instance.apiToken)
                }.body()
            }
        }

    private suspend fun downloadMissingFiles(instance: RemoteInstance) =
        either {
            val expectedFiles = app.files.getAll().bind()
            val missingFiles = expectedFiles.filterNot { it.getStorageFile().exists() }
            val totalSize = Filesize.humanFriendly(missingFiles.sumOf { it.size })
            log.info("Number of missing files: ${missingFiles.size} ($totalSize)")
            missingFiles.forEach { file ->
                log.info("Downloading ${file.id}: ${file.originalFilename} (${Filesize.humanFriendly(file.size)})")
                downloadFile(instance, file).fold({
                    log.error("Error downloading ${file.id}: ${it.message}")
                }, {
                    log.info("${file.originalFilename} downloaded")
                })
            }
        }

    private suspend fun downloadFile(instance: RemoteInstance, file: FileDesc): AppResult<FileDesc> =
        syncLog.use(FileSyncId(file)) {
            catchResult {
                either {
                    HttpClient(CIO) {
                        expectSuccess = true
                        install(HttpTimeout) {
                            requestTimeoutMillis = 10 * 60_000 // 10 minutes
                            connectTimeoutMillis = 30_000
                            socketTimeoutMillis = 10 * 60_000
                        }
                    }.use {
                        val tempFile = catchResult {
                            val response = it.get("${instance.address}/sync/file/${file.id}") {
                                bearerAuth(instance.apiToken)
                            }
                            val channel: ByteReadChannel = response.body()
                            val tempFile = kotlin.io.path.createTempFile().toFile()
                            channel.copyTo(tempFile.outputStream())

                            val dlSize = tempFile.toPath().fileSize()
                            if (dlSize != file.size) {
                                raise(
                                    SyncError(
                                        "File size mismatch: expected ${file.size} bytes (${Filesize.humanFriendly(file.size)}), got: $dlSize bytes (${
                                            Filesize.humanFriendly(dlSize)
                                        })"
                                    )
                                )
                            }

                            file.checksum?.let {
                                val dlChecksum = FileChecksums.md5sum(tempFile).bind()
                                if (dlChecksum != file.checksum) {
                                    raise(SyncError("Checksum mismatch: expected ${file.checksum}, got ${dlChecksum}"))
                                }
                            }

                            tempFile
                        }.bind()
                        app.files.replaceFile(file.id, tempFile).bind()
                    }
                }
            }.flatten()
        }

    private fun hashToken(token: String): String = BCrypt.hashpw(token, BCrypt.gensalt())
}

@Serializable
data class RemoteInstance(
    @Field("Remote address", presentation = FieldPresentation.url)
    @Serializable(with = URISerializer::class)
    @NotEmpty
    @ValidURI
    val address: URI,
    @Field("Secret Token", presentation = FieldPresentation.secret)
    @NotEmpty
    val apiToken: String,
) : Validateable<RemoteInstance> {
    companion object {
        val EMPTY = RemoteInstance(URI(""), "")
    }
}

