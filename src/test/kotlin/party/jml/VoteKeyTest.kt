package party.jml

import arrow.core.raise.either
import it.skrape.matchers.toBe
import it.skrape.matchers.toBePresent
import party.jml.partyboi.auth.UserCredentials
import party.jml.partyboi.settings.AutomaticVoteKeys
import party.jml.partyboi.settings.VoteSettings
import party.jml.partyboi.voting.VoteKeyForm
import party.jml.partyboi.voting.VoteKeyRow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test


class VoteKeyTest : PartyboiTester {
    @Test
    fun testVoteKeyRegistration() = test {
        var voteKey: VoteKeyRow? = null
        setupServices {
            either {
                val keySet = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                voteKeys.createTickets(1, keySet).bind()
                voteKey = voteKeys.getVoteKeySet(keySet).bind().first()
            }
        }

        // Redirect anonymous user to login
        it.get("/vote/register") {
            findFirst("input[value=Login]").toBePresent
        }

        // Show vote key registartion for logged-in user
        it.login("user")
        it.get("/vote/register") {
            findFirst("form header") { text.toBe("Enable voting") }
        }

        // Registration with too short key fails
        it.post("/vote/register", VoteKeyForm("hunter2")) {
            findFirst(".error") { text.toBe("Minimum length is 8 characters") }
        }

        // Registration with too long key fails
        it.post("/vote/register", VoteKeyForm("don't you know who i am")) {
            findFirst(".error") { text.toBe("Maximum length is 8 characters") }
        }

        // Registration with invalid key fails
        it.post("/vote/register", VoteKeyForm("--------")) {
            findFirst(".error") { text.toBe("The code is invalid or already used") }
        }

        // Registration with correct key works
        it.post("/vote/register", VoteKeyForm(voteKey!!.key.id!!)) {
            it.redirectsTo("/vote")
        }

        // Registration again fails
        it.post("/vote/register", VoteKeyForm(voteKey.key.id!!)) {
            findFirst(".error") { text.toBe("You cannot register a vote key because you have voting rights already enabled.") }
        }

        // Registration by another user with the same key again fails
        it.login("otherUser")
        it.post("/vote/register", VoteKeyForm(voteKey.key.id!!)) {
            findFirst(".error") { text.toBe("The code is invalid or already used") }
        }
    }

    @Test
    fun `user is granted with voting rights when their email address is added to the vote key list`() = test {
        val user = UserCredentials(
            name = "john",
            password = "password",
            password2 = "password",
            email = "john@doe.com",
        )

        val voteSettings = VoteSettings(
            automaticVoteKeys = AutomaticVoteKeys.PER_EMAIL,
            listOfEmails = "",
            verifiedEmailsOnly = false
        )

        setupServices {
            val app = this
            either {
                addTestAdmin(app).bind()
                users.addUser(user, "127.0.0.1").bind()
                settings.saveSettings(voteSettings).bind()
            }
        }

        // Expect that user does not have voting rights yet
        it.login(user.name)
        it.get("/") {
            findFirst("a[href='/vote/register']") {
                text.toBe("Register vote key")
            }
        }

        // Add their email address to automatic vote key receivers
        val newSettings = voteSettings.copy(listOfEmails = user.email)
        it.login("admin")
        it.post("/admin/voting/settings", newSettings) {
            it.redirectsTo("/admin/voting")
        }

        // Expect that user has voting rights now
        it.login(user.name)
        it.get("/") {
            findFirst("a[href='/vote']") {
                text.toBe("Voting")
            }
            findFirst(".snackbars li span") {
                text.toBe("You have been granted rights to vote.")
            }
        }
    }
}