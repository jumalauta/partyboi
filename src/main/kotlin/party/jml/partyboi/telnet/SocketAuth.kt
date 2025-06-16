package party.jml.partyboi.telnet

import arrow.core.flatMap
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.UserCredentials

class SocketAuthMenu : SocketMenu {
    override fun getTitle(): String = "Welcome!"

    override suspend fun getItems(state: SocketState, app: AppServices): Map<String, String> = mapOf(
        "L" to "Login",
        "R" to "Register",
    )

    override suspend fun input(query: String, state: SocketState, app: AppServices): SocketState? =
        when (formattedQuery(query)) {
            "L" -> state.goto(SocketLogin())
            "R" -> state.goto(SocketRegistration())
            "Q" -> null
            else -> state
        }
}

data class SocketLogin(
    val username: String? = null,
    val err: String? = null
) : SocketPage {
    override fun getTitle(): String = "Login"

    override suspend fun print(state: SocketState, app: AppServices): String =
        if (username == null) {
            listOfNotNull(
                err?.let { "\n$it\n\n" },
                "Username: "
            ).joinToString("")
        } else {
            "Password: "
        }

    override suspend fun input(
        query: String,
        state: SocketState,
        app: AppServices
    ): SocketState {
        return if (username == null) {
            state.goto(copy(username = query))
        } else {
            val password = query

            val user = app.users.getUser(username).flatMap {
                it.authenticate(password)
            }.getOrNull()

            if (user == null) {
                state.goto(
                    copy(
                        username = null,
                        err = "Invalid credentials"
                    )
                )
            } else {
                state.setUser(user).goto(SocketVotingCompoMenu())
            }
        }
    }
}

data class SocketRegistration(
    val username: String? = null,
    val err: String? = null
) : SocketPage {
    override fun getTitle(): String = "Registration"

    override suspend fun print(state: SocketState, app: AppServices): String =
        if (username == null) {
            listOfNotNull(
                err?.let { "\n$it\n\n" },
                "Username: "
            ).joinToString("")
        } else {
            "Password: "
        }

    override suspend fun input(
        query: String,
        state: SocketState,
        app: AppServices
    ): SocketState {
        return if (username == null) {
            state.goto(copy(username = query))
        } else {
            val credentials = UserCredentials(username, query, query, "")
            app.users.addUser(credentials, "127.0.0.1").fold({
                state.goto(
                    copy(
                        username = null,
                        err = it.message
                    )
                )
            }) {
                state.setUser(it).goto(SocketVotingCompoMenu())
            }
        }
    }
}