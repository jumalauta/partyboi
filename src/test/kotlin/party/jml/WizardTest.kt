package party.jml

import arrow.core.raise.either
import io.ktor.client.request.forms.*
import io.ktor.http.*
import it.skrape.matchers.toBe
import kotlin.test.Test
import kotlin.test.assertEquals

class WizardTest : PartyboiTester {
    @Test
    fun testWizardShownForAdminWhenIncomplete() = test {
        setupServices {
            either {
                addTestAdmin(this@setupServices).bind()
                settings.wizardCompleted.set(false).bind()
            }
        }
        it.login("admin")

        // Admin navigating to any admin page is redirected to the wizard.
        it.get("/admin/settings", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Welcome to Partyboi") }
            // The wizard asks for the party dates.
            findFirst("input[name=partyStartDate]") { attribute("type").toBe("date") }
            findFirst("input[name=partyDays]") { attribute("value").toBe("3") }
        }

        // Front page also redirects an incomplete-wizard admin.
        it.get("/", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Welcome to Partyboi") }
        }
    }

    @Test
    fun testWizardNotShownForNonAdmin() = test {
        setupServices {
            either {
                addTestUser(this@setupServices).bind()
                settings.wizardCompleted.set(false).bind()
            }
        }
        it.login()

        // Non-admin users on the front page do not get the wizard.
        it.get("/", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") {
                // Front page heading is the configured instance name, definitely not the wizard.
                assertEquals(false, text.contains("Welcome to Partyboi"))
            }
        }
    }

    @Test
    fun testWizardSaveRedirectsToVoteKeys() = test {
        setupServices {
            either {
                addTestAdmin(this@setupServices).bind()
                settings.wizardCompleted.set(false).bind()
            }
        }
        it.login("admin")

        it.post("/wizard", formData {
            append("resultsFileHeader", "")
            append("colorScheme", "Blue")
            append("timeZone", "Europe/Helsinki")
            append("partyStartDate", "2026-07-15")
            append("partyDays", "3")
        }) {
            it.redirectsTo("/admin/voting")
        }

        // After completing the wizard, normal admin pages are reachable again.
        it.get("/admin/settings", HttpStatusCode.OK) {
            relaxed = true
            findFirst("h1") { text.toBe("Settings") }
        }
    }
}
