package party.jml.partyboi

import arrow.core.getOrElse
import io.ktor.server.application.*
import io.ktor.util.logging.*
import party.jml.partyboi.assets.AssetsRepository
import party.jml.partyboi.auth.SessionRepository
import party.jml.partyboi.auth.UserRepository
import party.jml.partyboi.compos.CompoRepository
import party.jml.partyboi.compos.admin.CompoRunService
import party.jml.partyboi.data.PropertyRepository
import party.jml.partyboi.db.DatabasePool
import party.jml.partyboi.db.Migrations
import party.jml.partyboi.db.getDatabasePool
import party.jml.partyboi.email.EmailServiceFacade
import party.jml.partyboi.entries.EntryRepository
import party.jml.partyboi.entries.FileRepository
import party.jml.partyboi.entries.ScreenshotRepository
import party.jml.partyboi.messages.MessageRepository
import party.jml.partyboi.replication.ReplicationService
import party.jml.partyboi.schedule.EventRepository
import party.jml.partyboi.screen.ScreenService
import party.jml.partyboi.settings.SettingsService
import party.jml.partyboi.signals.SignalService
import party.jml.partyboi.system.ErrorRepository
import party.jml.partyboi.system.TimeService
import party.jml.partyboi.triggers.TriggerRepository
import party.jml.partyboi.voting.VoteKeyRepository
import party.jml.partyboi.voting.VoteService

class AppServices(
    val db: DatabasePool,
    val config: ConfigReader,
) {
    val properties = PropertyRepository(this)
    val settings = SettingsService(this)
    val time = TimeService(this)
    val users = UserRepository(this)
    val sessions = SessionRepository(db)
    val compos = CompoRepository(this)
    val entries = EntryRepository(this)
    val files = FileRepository(this)
    val votes = VoteService(this)
    val voteKeys = VoteKeyRepository(this)
    val compoRun = CompoRunService(this)
    val screen = ScreenService(this)
    val screenshots = ScreenshotRepository(this)
    val events = EventRepository(this)
    val triggers = TriggerRepository(this)
    val signals = SignalService()
    val assets = AssetsRepository(this)
    val replication = ReplicationService(this)
    val errors = ErrorRepository(this)
    val email = EmailServiceFacade(this)
    val messages = MessageRepository(this)

    companion object {
        var globalInstance: AppServices? = null
    }
}

suspend fun Application.services(): AppServices {
    return AppServices.globalInstance ?: run {
        val db = getDatabasePool()
        val migration = Migrations.migrate(db).getOrElse { it.throwError() }
        val app = AppServices(db, config())
        app.replication.setSchemaVersion(migration.targetSchemaVersion ?: migration.initialSchemaVersion)
        AppServices.globalInstance = app
        app
    }
}

abstract class Logging {
    val log = KtorSimpleLogger(this.javaClass.name)
}
