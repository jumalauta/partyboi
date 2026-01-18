package party.jml.partyboi.sync

import arrow.core.raise.either
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.data.InvalidConfiguration
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.catchResult
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
    private val db = DbSyncService(app)
    private val expectedApiKey = property<String?>("apiKey", null)
    private val remoteInstance = property<RemoteInstance?>("remoteInstance", null)

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

    suspend fun isValidToken(token: String): AppResult<Boolean> =
        expectedApiKey.get().map { it == token }

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

data class RemoteInstance(
    val address: URI,
    val apiToken: String,
)

