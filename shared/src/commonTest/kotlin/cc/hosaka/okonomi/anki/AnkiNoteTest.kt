package cc.hosaka.okonomi.anki

import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class AnkiNoteTest {

    private fun hit(vararg segments: TitleSegment, senses: List<String> = listOf("to eat")) = SearchHit(
        entryId = 1L,
        titleSegments = segments.toList(),
        traceLabels = emptyList(),
        senseLines = senses,
        isCommon = false,
    )

    @Test
    fun `a word with furigana splits into its written form and its reading`() {
        val note = hit(TitleSegment("食べる"), TitleSegment("たべる", readsPreviousSegment = true)).toAnkiNote(emptyList())

        assertEquals("食べる", note.word)
        assertEquals("たべる", note.reading)
    }

    @Test
    fun `a kana-only word is its own reading`() {
        val note = hit(TitleSegment("ありがとう")).toAnkiNote(emptyList())

        assertEquals("ありがとう", note.word)
        assertEquals("ありがとう", note.reading)
    }

    @Test
    fun `a reading shown beside its form rather than over it is still the reading`() {
        // The row's fallback for a reading the entry does not claim for
        // the form: it is drawn next to the form, not as furigana.
        val note = hit(TitleSegment("空オケ"), TitleSegment("カラオケ", readsPreviousSegment = false)).toAnkiNote(emptyList())

        assertEquals("カラオケ", note.reading)
    }

    @Test
    fun `the meaning is the first five glosses as dash lines in order with nothing marking the rest`() {
        // 寝る-shaped: more senses than a row shows, and senses with
        // several glosses. The row's own lines are capped and end in
        // "…"; the card must not inherit either.
        val row = hit(
            TitleSegment("寝る"),
            TitleSegment("ねる", readsPreviousSegment = true),
            senses = listOf("to sleep (lying down)", "to go to bed, to lie in bed", "to lie down …"),
        )
        val glosses = listOf(
            "to sleep (lying down)",
            "to go to bed",
            "to lie in bed",
            "to lie down",
            "to sleep (with someone)",
            "to stay in bed (with an illness)",
            "to lie idle",
        )

        val note = row.toAnkiNote(glosses)

        // Spelled out rather than derived from the limit, so a changed
        // cap shows here as well as in the code.
        assertEquals(
            "- to sleep (lying down)<br>- to go to bed<br>- to lie in bed<br>- to lie down<br>" +
                "- to sleep (with someone)",
            note.meaning,
        )
        assertEquals("ねる", note.reading)
    }

    @Test
    fun `an entry with five glosses or fewer keeps every one`() {
        val five = listOf("one", "two", "three", "four", "five")

        assertEquals("- one<br>- two<br>- three<br>- four<br>- five", hit(TitleSegment("五")).toAnkiNote(five).meaning)
        assertEquals("- one<br>- two", hit(TitleSegment("二")).toAnkiNote(five.take(2)).meaning)
    }

    @Test
    fun `a gloss with markup characters is escaped inside its dash line`() {
        val note = hit(TitleSegment("記号")).toAnkiNote(listOf("<tag> & co"))

        assertEquals("- &lt;tag&gt; &amp; co", note.meaning)
    }

    @Test
    fun `markup characters are escaped so Anki shows them as text`() {
        assertEquals("a &amp; b &lt;i&gt;c&lt;/i&gt;", ankiFieldHtml("a & b <i>c</i>"))
    }

    @Test
    fun `an ampersand is escaped once and not again inside an entity it made`() {
        assertEquals("&amp;lt;", ankiFieldHtml("&lt;"))
    }

    @Test
    fun `the field separator never reaches a field`() {
        assertEquals("ab", ankiFieldHtml("a\u001fb"))
    }

    @Test
    fun `line breaks of either kind become br`() {
        assertEquals("a<br>b<br>c", ankiFieldHtml("a\nb\r\nc"))
    }

    @Test
    fun `the fields go out in the note type's order with Word first`() {
        val note = AnkiNote(word = "w", reading = "r", meaning = "m")

        assertEquals(listOf("Word", "Reading", "Meaning"), AnkiNoteType.fields)
        assertEquals(listOf("w", "r", "m"), note.fields)
    }
}
