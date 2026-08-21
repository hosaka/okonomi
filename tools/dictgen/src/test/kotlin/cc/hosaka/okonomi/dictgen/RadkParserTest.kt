package cc.hosaka.okonomi.dictgen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class RadkParserTest {

    @Test
    fun parsesEucJpRadkfile() {
        val file = File.createTempFile("radkfile", "").apply {
            deleteOnExit()
            writeBytes(Fixtures.radk.toByteArray(charset("EUC-JP")))
        }
        val data = RadkParser.parse(file)
        assertEquals(mapOf("一" to 1L, "人" to 2L), data.radicalStrokes)
        assertEquals(listOf("食", "倉"), data.kanjiByRadical.getValue("一"))
        assertEquals(listOf("食"), data.kanjiByRadical.getValue("人"))
    }
}
