package party.jml.partyboi.sync

import party.jml.partyboi.AppServices
import party.jml.partyboi.Service

class SyncService(app: AppServices) : Service(app) {
    val db = DbSyncService(app)

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