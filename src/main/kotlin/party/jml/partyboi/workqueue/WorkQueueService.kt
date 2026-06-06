package party.jml.partyboi.workqueue

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging

class WorkQueueService(val app: AppServices) : Logging() {
    val repository = WorkQueueRepository(app)

    suspend fun addTask(task: Task) = repository.add(task)

    suspend fun start() {
        repository.recoverStalledTasks().onLeft { log.error("Failed to recover stalled tasks", it.throwable) }
        while (true) {
            try {
                repository.takeNext().fold({
                    delay(1000)
                }, { taskRow ->
                    val success = try {
                        taskRow.task.execute(app).isRight()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (error: Throwable) {
                        // A task that throws (e.g. a Docker image that cannot be pulled) must not
                        // crash the worker loop — log it and mark the task as failed.
                        log.error("Task ${taskRow.id} threw an exception", error)
                        false
                    }
                    repository.setState(taskRow.id, if (success) TaskState.Success else TaskState.Failed)
                })
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                // Never let a failure in the queue plumbing (e.g. a DB hiccup) kill the worker loop.
                log.error("Work queue iteration failed", error)
                delay(1000)
            }
        }
    }
}
