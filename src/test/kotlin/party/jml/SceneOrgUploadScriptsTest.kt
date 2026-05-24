package party.jml

import party.jml.partyboi.compos.admin.SceneOrgUploadScripts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneOrgUploadScriptsTest {
    @Test
    fun bashScriptStartsWithShebangAndBakesParty() {
        val script = SceneOrgUploadScripts.bashScript("myparty", 2026)
        assertTrue(script.startsWith("#!/usr/bin/env bash\n"), "should start with shebang")
        assertTrue(script.contains("""PARTY_NAME="myparty""""), "should bake party name")
        assertTrue(script.contains("""PARTY_YEAR="2026""""), "should bake party year")
        assertTrue(script.contains("ftp.scene.org"), "should target scene.org ftp")
        assertTrue(
            script.contains("/incoming/parties/\${PARTY_YEAR}/\${PARTY_NAME}"),
            "should build remote path under /incoming/parties/"
        )
        assertTrue(script.contains("anonymous:"), "should use anonymous FTP login")
    }

    @Test
    fun bashScriptUsesLfOnlyLineEndings() {
        val script = SceneOrgUploadScripts.bashScript("p", 2026)
        assertFalse(script.contains("\r"), "bash script must not contain CR characters")
    }

    @Test
    fun batchScriptHasEchoOffAndBakesParty() {
        val script = SceneOrgUploadScripts.batchScript("myparty", 2026)
        assertTrue(script.startsWith("@echo off"), "should start with @echo off")
        assertTrue(script.contains("set \"PARTY_NAME=myparty\""), "should bake party name")
        assertTrue(script.contains("set \"PARTY_YEAR=2026\""), "should bake party year")
        assertTrue(script.contains("ftp.scene.org"), "should target scene.org ftp")
        assertTrue(
            script.contains("/incoming/parties/%PARTY_YEAR%/%PARTY_NAME%"),
            "should build remote path under /incoming/parties/"
        )
        assertTrue(script.contains("anonymous:"), "should use anonymous FTP login")
    }

    @Test
    fun batchScriptUsesCrlfLineEndings() {
        val script = SceneOrgUploadScripts.batchScript("p", 2026)
        val lines = script.split("\r\n")
        assertTrue(lines.size > 5, "should have multiple CRLF-terminated lines")
        val strayLf = script.replace("\r\n", "").contains("\n")
        assertFalse(strayLf, "batch script must not contain lone LFs")
    }

    @Test
    fun bothScriptsSkipUploadingThemselves() {
        val bash = SceneOrgUploadScripts.bashScript("p", 2026)
        val bat = SceneOrgUploadScripts.batchScript("p", 2026)
        assertTrue(bash.contains(SceneOrgUploadScripts.BASH_FILENAME))
        assertTrue(bash.contains(SceneOrgUploadScripts.BATCH_FILENAME))
        assertTrue(bat.contains(SceneOrgUploadScripts.BASH_FILENAME))
        assertTrue(bat.contains(SceneOrgUploadScripts.BATCH_FILENAME))
    }

    @Test
    fun filenamesAreSceneOrgCompliant() {
        val pattern = Regex("^[a-z0-9_]+\\.[a-z0-9]+$")
        assertTrue(pattern.matches(SceneOrgUploadScripts.BASH_FILENAME))
        assertTrue(pattern.matches(SceneOrgUploadScripts.BATCH_FILENAME))
        assertEquals("upload.sh", SceneOrgUploadScripts.BASH_FILENAME)
        assertEquals("upload.bat", SceneOrgUploadScripts.BATCH_FILENAME)
    }
}
