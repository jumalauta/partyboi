package party.jml

import party.jml.partyboi.auth.UserCredentials
import party.jml.partyboi.settings.AutomaticVoteKeys
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailFieldDescriptionTest {
    @Test
    fun `email service not configured`() {
        assertEquals(
            "Optional: the organizers can use your email address to contact you about your entries. " +
                    "Emails given to Partyboi are never used for any other purpose.",
            UserCredentials.emailFieldDescription(
                emailServiceConfigured = false,
                automaticVoteKeys = AutomaticVoteKeys.DISABLED,
                verifiedEmailsOnly = true,
            )
        )
    }

    @Test
    fun `email service configured without email-based vote keys`() {
        assertEquals(
            "Optional but recommended: with an email address you can reset a forgotten password, " +
                    "and the organizers can contact you about your entries. " +
                    "Emails given to Partyboi are never used for any other purpose.",
            UserCredentials.emailFieldDescription(
                emailServiceConfigured = true,
                automaticVoteKeys = AutomaticVoteKeys.PER_USER,
                verifiedEmailsOnly = true,
            )
        )
    }

    @Test
    fun `email-based vote keys with verified emails only`() {
        assertEquals(
            "Optional but recommended: with an email address you can reset a forgotten password, " +
                    "and the organizers can contact you about your entries. " +
                    "It also grants you voting rights automatically — use the same email address you used " +
                    "when registering to the party, and confirm it via the verification link we send you. " +
                    "Emails given to Partyboi are never used for any other purpose.",
            UserCredentials.emailFieldDescription(
                emailServiceConfigured = true,
                automaticVoteKeys = AutomaticVoteKeys.PER_EMAIL,
                verifiedEmailsOnly = true,
            )
        )
    }

    @Test
    fun `email-based vote keys without email verification`() {
        assertEquals(
            "Optional but recommended: with an email address you can reset a forgotten password, " +
                    "and the organizers can contact you about your entries. " +
                    "It also grants you voting rights automatically — use the same email address you used " +
                    "when registering to the party. " +
                    "Emails given to Partyboi are never used for any other purpose.",
            UserCredentials.emailFieldDescription(
                emailServiceConfigured = true,
                automaticVoteKeys = AutomaticVoteKeys.PER_EMAIL,
                verifiedEmailsOnly = false,
            )
        )
    }
}
