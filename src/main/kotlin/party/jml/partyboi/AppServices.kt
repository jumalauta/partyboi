package party.jml.partyboi

import arrow.core.getOrElse
import io.ktor.server.application.*
import io.ktor.util.logging.*
import party.jml.partyboi.assets.AssetsRepository
import party.jml.partyboi.auth.SessionRepository
import party.jml.partyboi.auth.UserService
import party.jml.partyboi.compos.CompoRepository
import party.jml.partyboi.compos.admin.CompoRunService
import party.jml.partyboi.data.PersistentCachedValue
import party.jml.partyboi.data.PropertyRepository
import party.jml.partyboi.db.DatabasePool
import party.jml.partyboi.db.Migrations
import party.jml.partyboi.db.getDatabasePool
import party.jml.partyboi.email.EmailServiceFacade
import party.jml.partyboi.entries.EntryRepository
import party.jml.partyboi.entries.FileRepository
import party.jml.partyboi.entries.ScreenshotRepository
import party.jml.partyboi.ffmpeg.DockerFileShare
import party.jml.partyboi.ffmpeg.FfmpegService
import party.jml.partyboi.messages.MessageRepository
import party.jml.partyboi.schedule.EventRepository
import party.jml.partyboi.schedule.EventRepositoryImpl
import party.jml.partyboi.schedule.EventSignalEmitter
import party.jml.partyboi.screen.ScreenService
import party.jml.partyboi.settings.SettingsService
import party.jml.partyboi.signals.SignalService
import party.jml.partyboi.system.ErrorRepository
import party.jml.partyboi.system.TimeService
import party.jml.partyboi.triggers.TriggerRepository
import party.jml.partyboi.voting.VoteKeyRepository
import party.jml.partyboi.voting.VoteService
import party.jml.partyboi.workqueue.WorkQueueService

interface AppServices {
    val db: DatabasePool
    val config: ConfigReader
    val properties: PropertyRepository
    val settings: SettingsService
    val time: TimeService
    val users: UserService
    val sessions: SessionRepository
    val compos: CompoRepository
    val entries: EntryRepository
    val files: FileRepository
    val votes: VoteService
    val voteKeys: VoteKeyRepository
    val compoRun: CompoRunService
    val screen: ScreenService
    val screenshots: ScreenshotRepository
    val events: EventRepository
    val triggers: TriggerRepository
    val signals: SignalService
    val assets: AssetsRepository
    val errors: ErrorRepository
    val email: EmailServiceFacade
    val messages: MessageRepository
    val workQueue: WorkQueueService
    val dockerFileShare: DockerFileShare
    val ffmpeg: FfmpegService
    val eventSignalEmitter: EventSignalEmitter
}

class AppServicesImpl(
    override val db: DatabasePool,
    override val config: ConfigReader,
) : AppServices {
    override val properties = PropertyRepository(this)
    override val settings = SettingsService(this)
    override val time = TimeService(this)
    override val users = UserService(this)
    override val sessions = SessionRepository(db)
    override val compos = CompoRepository(this)
    override val entries = EntryRepository(this)
    override val files = FileRepository(this)
    override val votes = VoteService(this)
    override val voteKeys = VoteKeyRepository(this)
    override val compoRun = CompoRunService(this)
    override val screenshots = ScreenshotRepository(this)
    override val events = EventRepositoryImpl(this)
    override val triggers = TriggerRepository(this)
    override val signals = SignalService(this)
    override val screen = ScreenService(this)
    override val assets = AssetsRepository(this)
    override val errors = ErrorRepository(this)
    override val email = EmailServiceFacade(this)
    override val messages = MessageRepository(this)
    override val workQueue = WorkQueueService(this)
    override val dockerFileShare = DockerFileShare(this)
    override val ffmpeg = FfmpegService(this)
    override val eventSignalEmitter = EventSignalEmitter(this)

    companion object {
        var globalInstance: AppServicesImpl? = null
    }
}

suspend fun Application.services(): AppServices {
    return AppServicesImpl.globalInstance ?: run {
        val db = getDatabasePool()
        Migrations.migrate(db).getOrElse { it.throwError() }
        val app = AppServicesImpl(db, config())
        AppServicesImpl.globalInstance = app
        app
    }
}

abstract class Logging {
    val log = KtorSimpleLogger(this.javaClass.name)
}

abstract class Service(val app: AppServices) : Logging() {
    inline fun <reified T> property(key: String, value: T, private: Boolean = false): PersistentCachedValue<T> =
        app.properties.createPersistentCachedValue("${this::class.simpleName}.$key", private, value)
}

