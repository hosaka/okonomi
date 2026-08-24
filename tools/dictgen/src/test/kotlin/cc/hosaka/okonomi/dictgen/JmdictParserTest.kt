package cc.hosaka.okonomi.dictgen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JmdictParserTest {

    private fun parseFixture(content: String): List<JmdictEntry> {
        val file = File.createTempFile("jmdict", ".xml").apply {
            deleteOnExit()
            writeText(content)
        }
        val entries = mutableListOf<JmdictEntry>()
        JmdictParser(file).parse { entries += it }
        return entries
    }

    @Test
    fun parsesEntryWithShortPosCodes() {
        val entry = parseFixture(Fixtures.jmdict).single()
        assertEquals(1358280L, entry.id)
        assertEquals("v1,vt", entry.senses[0].pos)
        assertEquals("food", entry.senses[0].field)
    }

    @Test
    fun keepsOnlyEnglishGlosses() {
        val entry = parseFixture(Fixtures.jmdict).single()
        assertEquals(listOf("to eat"), entry.senses[0].glosses)
        assertEquals(listOf("to live on"), entry.senses[1].glosses)
    }

    @Test
    fun skipsExampleElements() {
        val entry = parseFixture(Fixtures.jmdict).single()
        assertTrue(entry.senses[0].glosses.none { it.contains("Eat quickly") })
    }

    @Test
    fun posCarriesForwardToLaterSenses() {
        val entry = parseFixture(Fixtures.jmdict).single()
        assertEquals("v1,vt", entry.senses[1].pos)
        assertEquals("uk", entry.senses[1].misc)
        assertEquals("colloquial", entry.senses[1].info)
    }

    @Test
    fun collapsesPriorityTags() {
        val entry = parseFixture(Fixtures.jmdict).single()
        // ichi1 + news2 + nf25: first tier, frequency band 25.
        assertEquals(125L, entry.kanjiForms.single().commonRank)
        assertEquals(125L, entry.readings[0].commonRank)
        assertTrue(entry.kanjiForms.single().isCommon)
        assertTrue(entry.readings[0].isCommon)
    }

    @Test
    fun readsNoKanjiAndRestrictions() {
        val entry = parseFixture(Fixtures.jmdict).single()
        val second = entry.readings[1]
        assertEquals("タベル", second.text)
        assertTrue(second.noKanji)
        assertEquals("食べる", second.restrictions)
        assertEquals(950L, second.commonRank)
        assertFalse(second.isCommon)
    }

    @Test
    fun unknownEntityFailsWithItsName() {
        val broken = Fixtures.jmdict.replace("&v1;", "&bogus;")
        val e = assertFailsWith<PipelineException> { parseFixture(broken) }
        assertTrue("bogus" in (e.message ?: ""), "message should name the entity: ${e.message}")
    }
}
