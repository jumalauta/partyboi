package party.jml

import party.jml.partyboi.data.UUIDv7
import party.jml.partyboi.triggers.OpenCloseVoting
import party.jml.partyboi.triggers.PendingTriggerRow
import kotlin.test.Test
import kotlin.test.assertTrue

class TriggerActionTest {

    // Regression: decoding a trigger whose type is no longer recognised (e.g. an action class renamed
    // in a refactor) used to throw NotImplementedError out of getAction(). Because the signal
    // collector ran that unguarded, a single such row killed the collector and silently disabled all
    // triggers for the rest of the process lifetime. getAction() must now return a Left instead.
    @Test
    fun testUnknownTriggerTypeYieldsLeftInsteadOfThrowing() {
        val row = PendingTriggerRow(
            triggerId = UUIDv7.Empty,
            signal = "someSignal",
            triggerType = "party.jml.partyboi.triggers.RenamedAction",
            actionJson = "{}",
            description = "stale trigger",
            enabled = true,
        )

        val result = row.getAction()

        assertTrue(result.isLeft(), "Unknown trigger type should yield a Left, not throw: $result")
    }

    @Test
    fun testKnownTriggerTypeStillDecodes() {
        val action = OpenCloseVoting(compoId = UUIDv7.Empty, open = true)
        val row = PendingTriggerRow(
            triggerId = UUIDv7.Empty,
            signal = "someSignal",
            triggerType = OpenCloseVoting::class.qualifiedName!!,
            actionJson = action.toJson(),
            description = "open voting",
            enabled = true,
        )

        val result = row.getAction()

        assertTrue(result.isRight(), "A known trigger type should still decode: $result")
    }
}
