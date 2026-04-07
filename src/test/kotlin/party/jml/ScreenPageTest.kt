package party.jml

import arrow.core.right
import io.ktor.http.*
import kotlin.test.Test

class ScreenPageTest : PartyboiTester {
    @Test
    fun testScreenPageLoads() = test {
        setupServices { Unit.right() }
        it.get("/screen", HttpStatusCode.OK) {}
    }
}