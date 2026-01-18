package party.jml.partyboi.sync

import arrow.core.raise.either
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json
import party.jml.partyboi.AppServices
import party.jml.partyboi.Service
import party.jml.partyboi.system.AppResult
import party.jml.partyboi.system.catchResult
import java.net.URL
import javax.ws.rs.client.Entity.json

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
    VoteKeys("votes_key")
}

class SyncService(app: AppServices) : Service(app) {
    private val db = DbSyncService(app)
    private val apiKey = property<String?>("apiKey", "hunter2")
    private val client = HttpClient(CIO) {
        expectSuccess = true
        json {
            Json {
                ignoreUnknownKeys = false
                isLenient = false
            }
        }
    }

    suspend fun isValidToken(token: String): AppResult<Boolean> =
        apiKey.get().map { it == token }

    suspend fun getTable(table: SyncedTable) = db.getTable(table.tableName)
    suspend fun putTable(table: Table) = db.putTable(table)

    suspend fun downloadAndMergeTables(instance: RemoteInstance) = either {
        SyncedTable.entries.forEach { table ->
            val importedData = downloadTable(instance, table).bind()
            db.putTable(importedData).bind()
        }
    }

    suspend fun downloadTable(instance: RemoteInstance, table: SyncedTable): AppResult<Table> =
        catchResult {
            client.get("${instance.url}/sync/table/${table.tableName}") {
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
    val url: URL,
    val apiToken: String,
)

