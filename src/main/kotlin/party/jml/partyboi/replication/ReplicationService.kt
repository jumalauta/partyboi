@file:UseSerializers(
    OptionSerializer::class,
)

package party.jml.partyboi.replication

import arrow.core.*
import arrow.core.computations.ResultEffect.bind
import arrow.core.raise.either
import arrow.core.serialization.OptionSerializer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotliquery.Row
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.Logging
import party.jml.partyboi.auth.User
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.data.*
import party.jml.partyboi.db.many
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.screen.ScreenRow
import party.jml.partyboi.screen.SlideSetRow
import party.jml.partyboi.triggers.TriggerRow
import party.jml.partyboi.voting.VoteRow
import java.io.File
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.io.path.exists

class ReplicationService(val app: AppServices) : Logging() {
    private var schemaVersion: String? = null
    private val importConfig = Config.getReplicationImportConfig()
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        importConfig.onSome {
            if (it.source == "https://localhost") {
                engine {
                    https {
                        trustManager = TrustAllX509TrustManager()
                    }
                }
            }
        }
    }

    init {
        Config.getReplicationExportApiKey().toOption().onSome {
            log.info("This instance can be replicated with api key: $it")
        }
        importConfig.onSome {
            log.info("This instance replicates ${it.source} with api key: ${it.apiKey}")
        }
    }

    val isReadReplica: Boolean = importConfig.isSome()

    suspend fun sync(): Either<AppError, Unit> = either {
        val response = makeRequest("export").bind()
        val data = response.body<DataExport>()
        import(data).bind()
        syncEntries(data.files).bind()
        syncScreenShots(data.files).bind()
        syncAssets(data.assets).bind()
    }.onLeft { log.error(it.toString(), it.throwable) }

    fun setSchemaVersion(version: String?) {
        schemaVersion = version
    }

    fun export(): Either<AppError, DataExport> {
        val version = schemaVersion
        return if (version == null) {
            log.error("Cannot export data because schema version is unknown. Database migration not ready?")
            NotReady().left()
        } else {
            either {
                DataExport(
                    schemaVersion = version,
                    users = app.users.getUsers().bind(),
                    compos = app.compos.getAllCompos().bind(),
                    entries = app.entries.getAllEntries().bind(),
                    events = app.events.getAll().bind(),
                    files = app.files.getAll().bind(),
                    properties = app.properties.getAll().bind(),
                    slides = app.screen.getAllSlides().bind(),
                    slideSets = app.screen.getSlideSets().bind(),
                    triggers = app.triggers.getAllTriggers().bind(),
                    votes = app.votes.getAllVotes().bind(),
                    voteKeys = getVoteKeys().bind(),
                    assets = app.assets.getList(),
                )
            }
        }
    }

    fun import(data: DataExport): Either<AppError, Unit> =
        if (data.schemaVersion != schemaVersion) {
            log.error("Cannot import data because the schema version mismatches. Own version: $schemaVersion, their version: ${data.schemaVersion}")
            InvalidSchemaVersion().left()
        } else {
            log.info("Start importing data...")
            app.db.createSchema("import").flatMap { schema ->
                app.db.transaction(schema) { tx ->
                    either {
                        app.users.import(tx, data).bind()
                        app.compos.import(tx, data).bind()
                        app.entries.import(tx, data).bind()
                        app.events.import(tx, data).bind()
                        app.files.import(tx, data).bind()
                        app.properties.import(tx, data).bind()
                        app.screen.import(tx, data).bind()
                        app.triggers.import(tx, data).bind()
                        app.votes.import(tx, data).bind()
                        log.info("Copy sessions")
                        app.db.copyRows(tx, "public.session", "$schema.session").bind()
                        log.info("Publish changes")
                        app.db.swapSchema(tx, schema, "public").bind()
                        log.info("Data imported succesfully")
                    }
                }.map {}
            }
        }

    @OptIn(InternalAPI::class)
    suspend fun downloadFile(path: String, checksum: String?, target: File): Either<AppError, Unit> = either {
        log.info("Check file $path (checksum: $checksum)")
        val response = makeRequest(path, checksum).bind()
        if (response.status == HttpStatusCode.OK) {
            log.info("Download $path to $target")
            Either.catch {
                target.toPath().parent.toFile().mkdirs()
                response.content.copyAndClose(target.writeChannel())
            }.mapLeft { InternalServerError(it) }.bind()
        } else {
            log.info("Skipped $path (${response.status})")
        }
    }

    suspend fun syncEntries(files: List<FileDesc>): Either<AppError, Unit> = either {
        val newFiles = files.filterNot { it.storageFilename.exists() }
        log.info("Sync ${newFiles.size} of ${files.size} entry files")
        newFiles.map {
            downloadFile(
                path = "entry/${it.storageFilename}",
                checksum = it.checksum,
                target = app.files.getStorageFile(it.storageFilename)
            )
        }.bindAll()
    }

    suspend fun syncScreenShots(files: List<FileDesc>): Either<AppError, Unit> = either {
        log.info("Sync ${files.size} screenshots")
        files
            .map {
                downloadFile(
                    path = "screenshot/${it.entryId}",
                    checksum = it.checksum,
                    target = app.screenshots.getFile(it.entryId).toFile()
                )
            }
            .bindAll()
    }

    suspend fun syncAssets(assets: List<String>): Either<AppError, Unit> = either {
        log.info("Sync ${assets.size} assets")
        assets
            .map {
                downloadFile(
                    path = "asset/$it",
                    checksum = app.assets.getChecksum(it).getOrNull(),
                    target = app.assets.getFile(it).toFile(),
                )
            }
            .bindAll()
    }

    private suspend fun makeRequest(
        replicationRoute: String,
        checksum: String? = null
    ): Either<AppError, HttpResponse> =
        importConfig
            .toEither { Notice("This is not a read replica") }
            .flatMap { config ->
                Either.catch {
                    val request = HttpRequestBuilder()
                    request.url("${config.source}/replication/$replicationRoute")
                    request.header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                    if (checksum != null) request.header("ETag", checksum)
                    val response = client.get(request)
                    if (response.status.value >= 400) {
                        throw Error("Unexpected response: ${response.status}")
                    }
                    response
                }.mapLeft { InternalServerError(it) }
            }

    // TODO: Move to an own repository
    private fun getVoteKeys(): Either<AppError, List<VoteKeyRow>> = app.db.use {
        it.many(queryOf("SELECT * FROM votekey").map(VoteKeyRow.fromRow))
    }
}

@Serializable
data class DataExport(
    val schemaVersion: String,
    val users: List<User>,
    val compos: List<Compo>,
    val entries: List<Entry>,
    val events: List<Event>,
    val files: List<FileDesc>,
    val properties: List<PropertyRow>,
    val slides: List<ScreenRow>,
    val slideSets: List<SlideSetRow>,
    val triggers: List<TriggerRow>,
    val votes: List<VoteRow>,
    val voteKeys: List<VoteKeyRow>,
    val assets: List<String>,
)

@Serializable
data class VoteKeyRow(
    val key: String,
    val userId: Option<Int>,
) {
    companion object {
        val fromRow: (Row) -> VoteKeyRow = { row ->
            VoteKeyRow(
                key = row.string("key"),
                userId = Option.fromNullable(row.intOrNull("appuser_id")),
            )
        }
    }
}

class TrustAllX509TrustManager : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOfNulls(0)
    override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
    override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
}