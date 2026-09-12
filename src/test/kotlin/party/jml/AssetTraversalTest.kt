package party.jml

import arrow.core.raise.either
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import party.jml.partyboi.form.FileUpload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetTraversalTest : PartyboiTester {

    // Regression: /assets/{path...} built the file path by string concatenation without normalising
    // it or checking containment, so url-encoded ".." segments escaped the assets directory and
    // served arbitrary files without authentication.
    @Test
    fun testAssetRouteRejectsPathTraversal() = test {
        setupServices {
            either {
                assets.write(FileUpload.fromByteArray("legit.dat", ByteArray(8))).bind()
                // A file next to (but outside) the assets directory must not be reachable.
                config.filesDir.resolve("outside-assets.txt").toFile().writeText("secret")
            }
        }

        assertEquals(HttpStatusCode.OK, it.client.get("/assets/legit.dat").status)

        val insideFilesDir = it.client.get("/assets/%2e%2e/outside-assets.txt")
        assertEquals(HttpStatusCode.NotFound, insideFilesDir.status)
        assertFalse(insideFilesDir.bodyAsText().contains("secret"))

        val traversal = "%2e%2e%2f".repeat(8) + "etc%2fpasswd"
        val resp = it.client.get("/assets/$traversal")
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertFalse(resp.bodyAsText().contains("root:"))
    }

    // Regression: AssetsRepository resolved client-supplied names (multipart filenames, delete
    // targets) against the assets directory without a containment check.
    @Test
    fun testAssetRepositoryRejectsEscapingNames() = test {
        setupServices {
            either {
                assertTrue(assets.write(FileUpload.fromByteArray("../evil.txt", ByteArray(8))).isLeft())
                assertTrue(assets.delete("../../etc/passwd").isLeft())
                assertTrue(assets.deleteDirectory("..").isLeft())
                // Subdirectories inside the assets directory are still allowed.
                assertTrue(assets.write(FileUpload.fromByteArray("sub/dir/ok.dat", ByteArray(8))).isRight())
            }
        }

        it.get("/", HttpStatusCode.OK) {}
    }
}
