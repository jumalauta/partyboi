package party.jml

import arrow.core.right
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import party.jml.partyboi.AppServices
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.signals.SignalType
import java.util.Collections
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SignalBusTest : PartyboiTester {

    // Regression: the signal bus was a conflating MutableStateFlow, so while a collector was busy
    // handling one signal, further distinct signals emitted in the same burst were dropped and only
    // the latest survived. A buffered SharedFlow must deliver every signal of the burst.
    @Test
    fun testSignalBurstIsNotConflated() = test {
        var appRef: AppServices? = null
        setupServices {
            appRef = this
            Unit.right()
        }

        it.login() // force application startup
        val signals = appRef!!.signals

        val n = 10
        val received = Collections.synchronizedList(mutableListOf<String>())
        val allReceived = CompletableDeferred<Unit>()

        coroutineScope {
            // A deliberately slow collector: with the old conflating StateFlow it only ever saw the
            // latest signal of a burst, dropping the ones emitted while it was busy.
            val base = signals.flow.subscriptionCount.value
            val collector = launch {
                signals.flow
                    .filter { it.type == SignalType.slideShown }
                    .collect { signal ->
                        received.add(signal.target!!)
                        delay(30)
                        if (received.size >= n) allReceived.complete(Unit)
                    }
            }
            // Wait until the collector has actually subscribed before emitting the burst.
            while (signals.flow.subscriptionCount.value <= base) delay(10)

            repeat(n) { i -> signals.emit(Signal.slideShown(UUID(0L, i.toLong()))) }

            withTimeout(5_000) { allReceived.await() }
            collector.cancel()
        }

        assertEquals(
            n,
            received.toList().distinct().size,
            "every distinct signal in the burst must be delivered, not conflated to the latest",
        )
    }
}
