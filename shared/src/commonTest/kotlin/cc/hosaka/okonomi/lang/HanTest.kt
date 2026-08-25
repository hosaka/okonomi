package cc.hosaka.okonomi.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HanTest {

    /** U+20B9F, an extension B ideograph written as a surrogate pair. */
    private val supplementary = "𠮟"

    @Test
    fun `takes the han characters of a mixed headword in order`() {
        assertEquals(listOf("食", "物"), hanCharacters("食べ物"))
    }

    @Test
    fun `collapses a repeat to one card keeping first appearance order`() {
        assertEquals(listOf("人"), hanCharacters("人人"))
        assertEquals(listOf("大", "学", "生"), hanCharacters("大学生大学"))
    }

    @Test
    fun `skips kana iteration marks and punctuation`() {
        // 々 is a symbol rather than an ideograph, so 日々 has one
        // character to show, not two.
        assertEquals(listOf("日"), hanCharacters("日々"))
        assertEquals(emptyList(), hanCharacters("ありがとう"))
        assertEquals(emptyList(), hanCharacters("ラーメン"))
        assertEquals(emptyList(), hanCharacters("・、。！?abc"))
    }

    @Test
    fun `keeps a supplementary-plane ideograph whole`() {
        assertEquals(
            listOf("猫", supplementary),
            hanCharacters("猫$supplementary"),
            "a surrogate pair must never be split into two half-characters",
        )
    }

    @Test
    fun `an empty string has no characters`() {
        assertEquals(emptyList(), hanCharacters(""))
    }

    @Test
    fun `recognizes every han block`() {
        assertTrue(isHanCodePoint(0x3400), "extension A")
        assertTrue(isHanCodePoint('食'.code), "unified ideographs")
        assertTrue(isHanCodePoint(0x9FFF), "unified ideographs, last")
        assertTrue(isHanCodePoint(0xF900), "compatibility ideographs, first")
        assertTrue(isHanCodePoint(0xFA6A), "compatibility 飯, the block JMdict actually reaches")
        assertTrue(isHanCodePoint(0xFAFF), "compatibility ideographs, last")
        assertTrue(isHanCodePoint(0x20B9F), "extension B")
        assertTrue(isHanCodePoint(0x30000), "extension G")
        assertTrue(isHanCodePoint(0x323AF), "extension H, last")
    }

    @Test
    fun `rejects kana latin and the marks the JVM predicate calls han`() {
        assertFalse(isHanCodePoint('べ'.code))
        assertFalse(isHanCodePoint('a'.code))
        assertFalse(isHanCodePoint(0x33FF), "just below extension A")
        assertFalse(isHanCodePoint(0x323B0), "just above extension H")
        // These are Han to Character.UnicodeScript but are marks and
        // radical forms rather than characters a learner looks up, so
        // they earn no card. See Han.kt on why the divergence is safe.
        assertFalse(isHanCodePoint('々'.code), "iteration mark")
        assertFalse(isHanCodePoint('〇'.code), "ideographic number zero")
        assertFalse(isHanCodePoint(0x3021), "Hangzhou numeral one")
        assertFalse(isHanCodePoint(0x2E80), "CJK radicals supplement")
        assertFalse(isHanCodePoint(0x2F00), "Kangxi radicals")
    }
}
