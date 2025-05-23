package party.jml.partyboi.telnet

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import party.jml.partyboi.AppServices

suspend fun Application.runSocketServer(port: Int, app: AppServices) {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", port)

    println("Server is listening at ${serverSocket.localAddress}")

    while (true) {
        val socket = serverSocket.accept()
        println("Accepted $socket")

        launch {
            val receiveChannel = socket.openReadChannel()
            val sendChannel = socket.openWriteChannel(autoFlush = true)
            SocketState().run(receiveChannel, sendChannel, app) {
                socket.close()
            }
        }
    }
}