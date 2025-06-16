package party.jml.partyboi.telnet

import io.ktor.utils.io.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.User

data class SocketState(
    val user: User? = null,
    val page: SocketPage = SocketAuthMenu(),
) {
    fun setUser(u: User) = copy(user = u)
    fun goto(page: SocketPage) =
        if (page.requiresAuthorization() && user == null) {
            copy(page = SocketLogin())
        } else {
            copy(page = page)
        }

    suspend fun run(
        rx: ByteReadChannel,
        tx: ByteWriteChannel,
        app: AppServices,
        closeConnection: () -> Unit
    ) {
        try {
            val header = "Partyboi BBS - ${page.getTitle()}"
            println("Output a page: $header")
            tx.writeStringUtf8("\u001B[2J\u001B[H")
            tx.writeStringUtf8("$header\n")
            tx.writeStringUtf8("${"-".repeat(header.length)}\n\n")
            tx.writeStringUtf8(page.print(this, app))

            println("Waiting for the user input...")
            val input = rx.readUTF8Line()
            println("Received input: $input")
            if (input != null) {
                val newState = page.input(input, this, app)
                if (newState == null) {
                    println("Input mapped to null, closing the connection...")
                    closeConnection()
                } else {
                    println("New state received. Execute the next state.")
                    newState.run(rx, tx, app, closeConnection)
                }
            }
        } catch (e: Throwable) {
            println("Error: ${e.localizedMessage}. Closing the connection...")
            closeConnection()
        }
    }
}

interface SocketPage {
    fun requiresAuthorization(): Boolean = false

    fun getTitle(): String
    suspend fun print(state: SocketState, app: AppServices): String
    suspend fun input(query: String, state: SocketState, app: AppServices): SocketState?

    fun formattedQuery(query: String): String = query.trim().uppercase()
}

interface AuthorizedSocketPage : SocketPage {
    override fun requiresAuthorization(): Boolean = true
}

interface SocketMenu : SocketPage {
    fun getMenuTitle(): String = "Select"
    suspend fun getItems(state: SocketState, app: AppServices): Map<String, String>

    override suspend fun print(state: SocketState, app: AppServices): String =
        (listOf(getMenuTitle() + ":", "") + getItems(state, app)
            .map { "  ${it.key}) ${it.value}" })
            .joinToString("\n") + "\n\n? "
}
