package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.system.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoteKeyGenerationTest : PartyboiTester {

    // Regression for the swallowed-error bug in generateTicket: the recursive call and the
    // insertVoteKey call were the last expression of an either {} that infers Unit, so their
    // Either results were coerced to Unit and never bound. A failing insert was therefore silently
    // dropped and createTickets reported success while committing nothing. Both calls must bind().
    @Test
    fun testCreateTicketsSurfacesInsertFailure() = test {
        var okCount = -1
        var failResult: AppResult<Unit>? = null

        setupServices {
            val app = this
            either {
                // Positive control: normal creation commits the requested number of tickets.
                app.voteKeys.createTickets(3, "ok").bind()
                okCount = app.db.useUnsafe {
                    one(queryOf("SELECT count(*) AS c FROM votekey WHERE key_set = 'ok'").map { it.int("c") })
                }.bind()

                // Force every votekey insert to fail, then confirm createTickets reports the failure.
                // NOT VALID skips the existing 'ok' rows but still enforces the check on new inserts.
                app.db.useUnsafe {
                    exec(queryOf("ALTER TABLE votekey ADD CONSTRAINT pb_reject_inserts CHECK (false) NOT VALID"))
                }.bind()
                try {
                    failResult = app.voteKeys.createTickets(3, "fail")
                } finally {
                    app.db.useUnsafe {
                        exec(queryOf("ALTER TABLE votekey DROP CONSTRAINT IF EXISTS pb_reject_inserts"))
                    }.bind()
                }
            }
        }

        it.client.get("/") // trigger application startup so setupServices runs

        assertEquals(3, okCount, "normal ticket creation should commit the requested tickets")
        assertTrue(
            failResult?.isLeft() == true,
            "createTickets must surface an insert failure instead of swallowing it, got $failResult",
        )
    }
}
