package party.jml.partyboi.syncharness

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private const val MASTER_URL = "http://localhost:18123"
private const val REMOTE_URL = "http://localhost:18124"

// What the remote uses to reach the master *inside* the docker compose network.
private const val MASTER_INTERNAL_URL = "http://appserver-master:8123"

fun main(args: Array<String>) {
    val skipDocker = args.contains("--no-docker")
    val keepStack = args.contains("--keep")

    val stack = DockerStack()

    println("======================================================================")
    println("Partyboi two-instance sync harness")
    println("  master: $MASTER_URL  (in-network: $MASTER_INTERNAL_URL)")
    println("  remote: $REMOTE_URL")
    println("======================================================================")

    var success = false
    try {
        runBlocking {
            if (!skipDocker) {
                println("[main] Bringing up docker compose stack")
                stack.up()
            } else {
                println("[main] --no-docker: assuming the stack is already up")
            }

            println("[main] Waiting for both servers to report healthy")
            waitForHealthy("master", MASTER_URL)
            waitForHealthy("remote", REMOTE_URL)

            InstanceClient("master", MASTER_URL).use { master ->
                InstanceClient("remote", REMOTE_URL).use { remote ->
                    val seed = Seeder(master).seed()

                    val verifier = Verifier(
                        master = master,
                        remote = remote,
                        masterInternalUrl = MASTER_INTERNAL_URL,
                        masterToken = seed.syncToken,
                    )
                    verifier.run()
                }
            }
        }
        success = true
    } catch (e: Throwable) {
        System.err.println()
        System.err.println("SYNC HARNESS: FAIL — ${e.message}")
        e.printStackTrace(System.err)
        if (!skipDocker) {
            runCatching {
                println("[main] Dumping recent logs from each appserver to aid debugging")
                stack.dumpLogs("appserver-master")
                stack.dumpLogs("appserver-remote")
            }
        }
    } finally {
        if (!skipDocker && !keepStack) {
            println("[main] Tearing down docker compose stack")
            stack.down()
        } else if (keepStack) {
            println("[main] --keep: leaving stack running for inspection")
        }
    }

    if (success) {
        println()
        println("SYNC HARNESS: PASS")
        exitProcess(0)
    } else {
        exitProcess(1)
    }
}

private suspend fun waitForHealthy(label: String, baseUrl: String, timeoutMs: Long = 120_000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    InstanceClient("$label-health", baseUrl).use { client ->
        while (true) {
            if (client.getHealth()) {
                println("[main] $label is healthy ($baseUrl)")
                return
            }
            if (System.currentTimeMillis() >= deadline) {
                error("$label at $baseUrl did not become healthy within ${timeoutMs / 1000}s")
            }
            delay(1500)
        }
    }
}
