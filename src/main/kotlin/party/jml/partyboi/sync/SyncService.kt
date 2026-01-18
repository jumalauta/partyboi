package party.jml.partyboi.sync

import arrow.core.raise.either
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mindrot.jbcrypt.BCrypt
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.InvalidConfiguration
import party.jml.partyboi.data.URISerializer
import party.jml.partyboi.data.randomToken
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.catchResult
import party.jml.partyboi.validation.NotEmpty
import party.jml.partyboi.validation.ValidURI
import party.jml.partyboi.validation.Validateable
import java.net.URI

enum class SyncedTable(val tableName: String) {
    Users("appuser"),
    Compos("compo"),
    Entries("entry"),
    EntryFiles("entry_file"),
    Events("event"),
    Files("file"),
    Messages("message"),
    Properties("property"),
    InfoScreens("screen"),
    SlideSets("slideset"),
    Triggers("trigger"),
    Votes("vote"),
    VoteKeys("votekey")
}

class SyncService(app: AppServices) : Service(app) {
    val expectedApiKey = property<String?>("apiKey", null)
    val remoteInstance = property<RemoteInstance?>("remoteInstance", null, private = true)

    private val db = DbSyncService(app)
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
    suspend fun putTable(table: Table) = db.putTable(table)

    suspend fun run() = either {
        val instance = remoteInstance.get().bind()
        if (instance == null) {
            raise(InvalidConfiguration())
        }
        downloadAndMergeTables(instance).bind()
    }

    private suspend fun downloadAndMergeTables(instance: RemoteInstance) = either {
        SyncedTable.entries.forEach { table ->
            val importedData = downloadTable(instance, table).bind()
            db.putTable(importedData).bind()
        }
    }

    suspend fun downloadTable(instance: RemoteInstance, table: SyncedTable): AppResult<Table> =
        catchResult {
            client.get("${instance.address}/sync/table/${table.name.lowercase()}") {
                accept(ContentType.Application.Json)
                bearerAuth(instance.apiToken)
            }.body()
        }

    private fun hashToken(token: String): String = BCrypt.hashpw(token, BCrypt.gensalt())

    companion object {
        val tables = listOf(
            "appuser",
            "compo",
            "entry",
            "entry_file",
            "event",
            "file",
            "message",
            "property",
            "screen",
            "slideset",
            "trigger",
            "vote",
            "votekey"
        )
    }
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

