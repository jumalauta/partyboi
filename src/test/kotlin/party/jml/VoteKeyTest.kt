package party.jml

import arrow.core.raise.either
import party.jml.partyboi.voting.VoteKeyRow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import it.skrape.matchers.*
import party.jml.partyboi.voting.VoteKeyForm


class VoteKeyTest : PartyboiTester {
    @Test
    fun testVoteKeyRegistration() = test {
        var voteKey: VoteKeyRow? = null
        services {
            either {
                voteKeys.deleteAll().bind()
                users.deleteUserByName("user")
                users.deleteUserByName("otherUser")

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
        it.post("/vote/register", VoteKeyForm(voteKey!!.key.id!!)) {
            findFirst(".error") { text.toBe("You cannot register a vote key because you have voting rights already enabled.") }
        }

        // Registration by another user with the same key again fails
        it.login("otherUser")
        it.post("/vote/register", VoteKeyForm(voteKey!!.key.id!!)) {
            findFirst(".error") { text.toBe("The code is invalid or already used") }
        }
    }
}