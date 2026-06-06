package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.entries.FileDesc
import party.jml.partyboi.workqueue.ExtractDuration
import party.jml.partyboi.workqueue.TaskRow
import party.jml.partyboi.workqueue.TaskState
import party.jml.partyboi.workqueue.WorkQueueRepository
import java.util.UUID
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkQueueRecoveryTest : PartyboiTester {

    // Regression: on startup the worker used to mark every 'Working' task 'Discarded', so a task that
    // was mid-flight when the process crashed/redeployed was lost forever (e.g. a preview never
    // generated). Stalled tasks must instead be requeued and retried a bounded number of times.
    @Test
    fun testStalledTasksAreRequeuedWithBoundedRetries() = test {
        var requeued: TaskRow? = null
        var exhausted: TaskRow? = null

        setupServices {
            val app = this
            either {
                val repo = app.workQueue.repository
                val sampleTask = ExtractDuration(sampleFileDesc())

                // A task interrupted mid-flight (Working) with attempts left -> requeued to Pending.
                val a = repo.add(sampleTask).bind()
                app.db.useUnsafe {
                    exec(queryOf("UPDATE task SET state = 'Working', started_at = now(), attempts = 1 WHERE id = ?", a.id))
                }.bind()

                // A task that has used all its attempts -> Failed, not requeued (no infinite restart loop).
                val b = repo.add(sampleTask).bind()
                app.db.useUnsafe {
                    exec(
                        queryOf(
                            "UPDATE task SET state = 'Working', started_at = now(), attempts = ? WHERE id = ?",
                            WorkQueueRepository.MAX_ATTEMPTS,
                            b.id,
                        )
                    )
                }.bind()

                repo.recoverStalledTasks().bind()

                requeued = repo.getById(a.id).bind()
                exhausted = repo.getById(b.id).bind()
            }
        }

        it.client.get("/") // trigger application startup so setupServices runs

        assertEquals(TaskState.Pending, requeued?.state, "an interrupted task with attempts left must be requeued")
        assertEquals(1, requeued?.attempts, "requeue keeps the attempt count")
        assertNull(requeued?.startedAt, "requeue clears started_at")
        assertEquals(TaskState.Failed, exhausted?.state, "a task that exhausted its attempts must be failed, not requeued")
    }

    private fun sampleFileDesc() = FileDesc(
        id = UUID.randomUUID(),
        originalFilename = "sample.mp3",
        deprecatedStorageFilename = null,
        type = "audio",
        size = 0L,
        uploadedAt = Instant.parse("2025-01-01T00:00:00Z"),
        checksum = null,
        processed = false,
        info = null,
    )
}
