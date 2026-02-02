package party.jml.partyboi.sync

import arrow.core.flatten
import arrow.core.raise.either
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
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
import java.util.*
import kotlin.io.path.createTempFile
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

    private val jsonClient = HttpClient(CIO) {
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

    private val fileClient = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 10 * 60_000 // 10 minutes
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 10 * 60_000
        }
        engine {
            pipelining = false
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

    suspend fun syncDown() = either {
        val instance = remoteInstance.get().bind()
        if (instance == null) {
            raise(InvalidConfiguration())
        }
        downloadAndMergeTables(instance).bind()
        downloadMissingFiles(instance).bind()
    }

    suspend fun syncUp() = either {
        val instance = remoteInstance.get().bind()
        if (instance == null) {
            raise(InvalidConfiguration())
        }
        uploadTables(instance).bind()
        uploadMissingFiles(instance).bind()
    }

    suspend fun getLog() = syncLog.getAll()

    private suspend fun downloadAndMergeTables(instance: RemoteInstance) = either {
        SyncedTable.entries.forEach { table ->
            val importedData = downloadTable(instance, table).bind()
            putTable(importedData).bind()
            log.info("Table ${table.tableName} synced successfully (${importedData.data.size} entries)")
        }
    }

    private suspend fun uploadTables(instance: RemoteInstance) = either {
        SyncedTable.entries.forEach { table ->
            val exportedData = getTable(table).bind()
            uploadTable(instance, exportedData).bind()
        }
    }

    private suspend fun downloadTable(instance: RemoteInstance, table: SyncedTable): AppResult<Table> =
        syncLog.use(TableDownSyncId(table.tableName)) {
            catchResult {
                jsonClient.get("${instance.address}/sync/table/${table.name.lowercase()}") {
                    accept(ContentType.Application.Json)
                    bearerAuth(instance.apiToken)
                }.body()
            }
        }

    private suspend fun uploadTable(instance: RemoteInstance, table: Table): AppResult<HttpResponse> =
        syncLog.use(TableUpSyncId(table.table)) {
            catchResult {
                jsonClient.post("${instance.address}/sync/table") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(instance.apiToken)
                    setBody(table)
                }
            }
        }

    suspend fun getMissingFiles(): AppResult<List<FileDesc>> = either {
        val expectedFiles = app.files.getAll().bind()
        expectedFiles.filterNot { it.getStorageFile().exists() }
    }

    private suspend fun downloadMissingFiles(instance: RemoteInstance) =
        either {
            val missingFiles = getMissingFiles().bind()
            val totalSize = Filesize.humanFriendly(missingFiles.sumOf { it.size })
            log.info("Number of missing files: ${missingFiles.size} ($totalSize)")
            missingFiles
                .sortedBy { it.size }
                .forEach { file ->
                    log.info("Downloading ${file.id}: ${file.originalFilename} (${Filesize.humanFriendly(file.size)})")
                    downloadFile(instance, file).fold({
                        log.error("Error downloading ${file.id}: ${it.message}")
                    }, {
                        log.info("${file.originalFilename} downloaded")
                    })
                }
        }

    private suspend fun uploadMissingFiles(instance: RemoteInstance) =
        either {
            val missingFileIds = getListOfMissingRemoteFiles(instance).bind().fileIds.map { UUID.fromString(it) }
            val filesForUpload = app.files
                .getAll().bind()
                .filter { missingFileIds.contains(it.id) }
                .filter { it.getStorageFile().exists() }
                .sortedBy { it.size }
            log.info(
                "Remote instance is missing ${missingFileIds.size} files. We have ${filesForUpload.size} files to upload (${
                    Filesize.humanFriendly(
                        filesForUpload.sumOf { it.size })
                })"
            )
            filesForUpload.forEach { file -> uploadFile(instance, file).bind() }
        }

    private suspend fun uploadFile(instance: RemoteInstance, file: FileDesc) =
        syncLog.use(FileUploadId(file)) {
            log.info("Uploading ${file.id}: ${file.originalFilename}")
            catchResult {
                fileClient.post("${instance.address}/sync/file/${file.id}") {
                    bearerAuth(instance.apiToken)
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    "file",
                                    file.getChannelProvider(),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "application/octet-stream")
                                        append(
                                            HttpHeaders.ContentDisposition,
                                            "filename=\"${file.id}\""
                                        )
                                    })
                            }
                        )
                    )
                }
            }
        }

    private suspend fun downloadFile(instance: RemoteInstance, file: FileDesc): AppResult<FileDesc> =
        syncLog.use(FileDownloadId(file)) {
            catchResult {
                either {
                    val tempFile = catchResult {
                        val response = fileClient.get("${instance.address}/sync/file/${file.id}") {
                            bearerAuth(instance.apiToken)
                        }
                        val channel: ByteReadChannel = response.body()
                        val tempFile = createTempFile().toFile()

                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                            while (!channel.isClosedForRead) {
                                val bytesRead = channel.readAvailable(buffer)
                                if (bytesRead == -1) break

                                output.write(buffer, 0, bytesRead)
                                output.flush() // important for long downloads
                            }
                        }

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
            }.flatten()
        }

    private suspend fun getListOfMissingRemoteFiles(instance: RemoteInstance): AppResult<MissingFiles> =
        syncLog.use(MissingFileList()) {
            catchResult {
                jsonClient.get("${instance.address}/sync/missing-files") {
                    accept(ContentType.Application.Json)
                    bearerAuth(instance.apiToken)
                }.body()
            }
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

@Serializable
data class MissingFiles(
    val fileIds: List<String>,
) {
    companion object {
        fun of(files: List<FileDesc>): MissingFiles =
            MissingFiles(
                fileIds = files.map { it.id.toString() }
            )
    }
}
