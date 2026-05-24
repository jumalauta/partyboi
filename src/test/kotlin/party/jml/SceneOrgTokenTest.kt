package party.jml

import party.jml.partyboi.data.toSceneOrgToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneOrgTokenTest {
    private val allowed = Regex("^[a-z0-9_]+$")

    @Test
    fun spacesCollapseToSingleUnderscore() {
        assertEquals("john_doe", "John Doe".toSceneOrgToken())
        assertEquals("john_doe", "  John   Doe  ".toSceneOrgToken())
    }

    @Test
    fun punctuationCollapses() {
        assertEquals("john_doe_tpb", "John-Doe (TPB)!".toSceneOrgToken())
        assertEquals("a_b_c", "a/b\\c".toSceneOrgToken())
    }

    @Test
    fun finnishAccentsMap() {
        assertEquals("paa_haa", "Pää & Hää".toSceneOrgToken())
        assertEquals("aaooaa", "ääööåå".toSceneOrgToken())
    }

    @Test
    fun lengthCapAndTrailingUnderscoreTrimmed() {
        val long = "abcdefghij".repeat(10)
        val out = long.toSceneOrgToken(maxLength = 8)!!
        assertEquals(8, out.length)
        assertEquals("abcdefgh", out)
        assertEquals("abc", "abc-----------xxx".toSceneOrgToken(maxLength = 4))
    }

    @Test
    fun emptyOrPunctuationOnlyReturnsNull() {
        assertNull("".toSceneOrgToken())
        assertNull("   ".toSceneOrgToken())
        assertNull("---!!!".toSceneOrgToken())
        assertNull("_".toSceneOrgToken())
    }

    @Test
    fun outputAlwaysMatchesSceneOrgPattern() {
        val inputs = listOf(
            "John Doe",
            "ÄLLÄ-PÖLLÄ #1",
            "Demo by Some Group [feat. Cool Author]",
            "title with: weird/chars\\here.ext",
            "____leading_and_trailing____",
            "a",
            "123",
            "mp3"
        )
        inputs.forEach { input ->
            val token = input.toSceneOrgToken()
            assertTrue(token != null && allowed.matches(token), "input '$input' produced '$token'")
        }
    }
}
