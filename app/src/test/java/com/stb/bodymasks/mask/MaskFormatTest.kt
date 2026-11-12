package com.stb.bodymasks.mask

import com.stb.bodymasks.pose.PosePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MaskFormatTest {
    @Test
    fun parsesMinimalRig() {
        val mask = MaskFormat.parse(
            """
            {
              "formatVersion": 1,
              "id": "demo",
              "name": "Demo",
              "parts": [
                {
                  "id": "head",
                  "src": "head.png",
                  "zIndex": 3,
                  "anchor": "NOSE",
                  "aimFrom": "NOSE",
                  "aimTo": "MID_SHOULDERS",
                  "sizeFrom": "LEFT_SHOULDER",
                  "sizeTo": "RIGHT_SHOULDER",
                  "pivot": { "x": 0.5, "y": 0.6 },
                  "lengthScale": 1.1,
                  "widthScale": 1.0
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals("demo", mask.id)
        assertEquals(PosePoint.NOSE, mask.parts.single().anchor)
        assertEquals(0.5f, mask.parts.single().minVisibility)
        assertEquals(false, mask.parts.single().flipHorizontal)
    }

    @Test(expected = MaskFormatException::class)
    fun rejectsUnknownVersion() {
        MaskFormat.parse("""{"formatVersion":2,"id":"a","name":"A","parts":[]}""")
    }

    @Test(expected = MaskFormatException::class)
    fun rejectsUnknownPoint() {
        MaskFormat.parse(
            """
            {
              "formatVersion": 1,
              "id": "demo",
              "name": "Demo",
              "parts": [{
                "id": "head",
                "src": "head.png",
                "zIndex": 1,
                "anchor": "TAIL",
                "aimFrom": "NOSE",
                "aimTo": "NOSE",
                "sizeFrom": "NOSE",
                "sizeTo": "NOSE",
                "pivot": {"x": 0.5, "y": 0.5},
                "lengthScale": 1,
                "widthScale": 1
              }]
            }
            """.trimIndent(),
        )
    }

    @Test(expected = MaskFormatException::class)
    fun rejectsPathTraversal() {
        MaskFormat.parse(
            """
            {
              "formatVersion": 1,
              "id": "demo",
              "name": "Demo",
              "parts": [{
                "id": "head",
                "src": "../secret.png",
                "zIndex": 1,
                "anchor": "NOSE",
                "aimFrom": "NOSE",
                "aimTo": "MID_SHOULDERS",
                "sizeFrom": "NOSE",
                "sizeTo": "NOSE",
                "pivot": {"x": 0.5, "y": 0.5},
                "lengthScale": 1,
                "widthScale": 1
              }]
            }
            """.trimIndent(),
        )
    }

    @Test
    fun bundledMasksAreValid() {
        val root = masksDir()
        val folders = root.listFiles { file -> file.isDirectory }.orEmpty()
        assertTrue(folders.any { it.name == "robot" })
        assertTrue(folders.any { it.name == "esqueleto" })
        for (folder in folders) {
            val rig = folder.resolve("rig.json")
            val mask = MaskFormat.parse(rig.readText())
            assertEquals(folder.name, mask.id)
            assertTrue(mask.parts.isNotEmpty())
            for (part in mask.parts) {
                val image = folder.resolve(part.src)
                assertTrue("Falta ${image.path}", image.isFile && image.length() > 32)
                val header = image.inputStream().use { it.readNBytes(8) }
                assertEquals(0x89.toByte(), header[0])
                assertEquals('P'.code.toByte(), header[1])
                assertEquals('N'.code.toByte(), header[2])
                assertEquals('G'.code.toByte(), header[3])
            }
        }
    }

    private fun masksDir(): File {
        val candidates = listOf(
            File("src/main/assets/masks"),
            File("app/src/main/assets/masks"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("No está la carpeta de máscaras. cwd=${File(".").absolutePath}")
    }
}
