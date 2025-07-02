package party.jml.partyboi.workqueue

import kotlinx.coroutines.delay
import party.jml.partyboi.AppServices

class WorkQueueService(val app: AppServices) {
    val repository = WorkQueueRepository(app)

    suspend fun addTask(task: Task) = repository.add(task)

    suspend fun start() {
        repository.cancel()
        while (true) {
            repository.takeNext().fold({
                repository.cancel()
                delay(1000)
            }, { taskRow ->
                val success = taskRow.task.execute(app).isRight()
                repository.setState(taskRow.id!!, if (success) TaskState.Success else TaskState.Failed)
            })
        }
    }
}