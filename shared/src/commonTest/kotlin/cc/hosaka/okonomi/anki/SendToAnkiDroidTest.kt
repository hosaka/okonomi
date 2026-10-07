package cc.hosaka.okonomi.anki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendToAnkiDroidTest {

    private val reports = mutableListOf<String>()
    private val report: (String, Throwable?) -> Unit = { message, _ -> reports += message }

    private fun words(range: IntRange) = range.map { AnkiNote(word = "word$it", reading = "r$it", meaning = "m$it") }

    @Test
    fun `a first send creates the deck and the note type and adds every word`() {
        val anki = FakeAnkiDroid()

        val result = sendToAnkiDroid(anki, words(1..10), report)

        assertEquals(AnkiSendResult.Sent(added = 10, alreadyThere = 0), result)
        assertEquals(listOf(ANKI_DECK_NAME), anki.decks.values.toList())
        assertEquals(listOf(AnkiNoteType.NAME), anki.noteTypes.values.toList())
        val deckId = anki.decks.keys.single()
        val noteTypeId = anki.noteTypes.keys.single()
        assertEquals(words(1..10).map { Triple(noteTypeId, deckId, it.fields) }, anki.notes)
    }

    @Test
    fun `the note type makes a forward and a reverse card from each note`() {
        val (forward, reverse) = AnkiNoteType.templates

        assertEquals(2, AnkiNoteType.templates.size)
        assertEquals("{{Word}}", forward.front)
        assertTrue("{{Reading}}" in forward.back && "{{Meaning}}" in forward.back, forward.back)
        assertTrue("{{Reading}}" in reverse.front && "{{Meaning}}" in reverse.front, reverse.front)
        assertTrue("{{Word}}" !in reverse.front, reverse.front)
        assertTrue("{{Word}}" in reverse.back, reverse.back)
    }

    @Test
    fun `a re-send adds only the words not already there and reuses the deck and note type`() {
        val anki = FakeAnkiDroid()
        sendToAnkiDroid(anki, words(1..10), report)

        val result = sendToAnkiDroid(anki, words(1..13), report)

        assertEquals(AnkiSendResult.Sent(added = 3, alreadyThere = 10), result)
        assertEquals(13, anki.notes.size)
        assertEquals(words(1..13).map { it.word }, anki.notes.map { it.third.first() })
        assertEquals(1, anki.decks.size)
        assertEquals(1, anki.noteTypes.size)
    }

    @Test
    fun `a capped meaning reaches AnkiDroid as built`() {
        val anki = FakeAnkiDroid()
        val glosses = listOf(
            "to sleep (lying down)", "to go to bed", "to lie in bed", "to lie down", "to lie idle", "to rest",
        )
        val note = AnkiNote(word = "寝る", reading = "ねる", meaning = ankiFieldHtml(ankiMeaning(glosses)))

        sendToAnkiDroid(anki, listOf(note), report)

        val meaning = anki.notes.single().third[2]
        assertEquals(glosses.take(5).map { "- $it" }, meaning.split("<br>"))
        assertTrue("…" !in meaning, meaning)
    }

    @Test
    fun `nothing new to send adds nothing`() {
        val anki = FakeAnkiDroid()
        sendToAnkiDroid(anki, words(1..5), report)

        val result = sendToAnkiDroid(anki, words(1..5), report)

        assertEquals(AnkiSendResult.NothingNew, result)
        assertEquals(5, anki.notes.size)
        assertEquals(listOf(5), anki.batches)
    }

    @Test
    fun `a deck the reader deleted is made again`() {
        val anki = FakeAnkiDroid()
        sendToAnkiDroid(anki, words(1..2), report)
        anki.decks.clear()

        val result = sendToAnkiDroid(anki, words(3..4), report)

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), result)
        val deckId = anki.decks.entries.single { it.value == ANKI_DECK_NAME }.key
        assertEquals(listOf(deckId, deckId), anki.notes.drop(2).map { it.second })
    }

    @Test
    fun `a note type the reader deleted is made again and its words are sent afresh`() {
        val anki = FakeAnkiDroid()
        sendToAnkiDroid(anki, words(1..2), report)
        // Deleting a note type in Anki deletes its notes with it.
        anki.noteTypes.clear()
        anki.notes.clear()

        val result = sendToAnkiDroid(anki, words(1..2), report)

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), result)
        assertEquals(1, anki.noteTypes.size)
    }

    @Test
    fun `a deck that differs only in case is the same deck`() {
        val anki = FakeAnkiDroid()
        anki.decks[1L] = ANKI_DECK_NAME.lowercase()

        val result = sendToAnkiDroid(anki, words(1..1), report)

        assertEquals(AnkiSendResult.Sent(added = 1, alreadyThere = 0), result)
        assertEquals(listOf(1L), anki.notes.map { it.second })
    }

    @Test
    fun `a thousand words go in batches no larger than the limit and all land`() {
        val anki = FakeAnkiDroid()

        val result = sendToAnkiDroid(anki, words(1..1_000), report)

        assertEquals(AnkiSendResult.Sent(added = 1_000, alreadyThere = 0), result)
        assertEquals(1_000, anki.batches.sum())
        assertTrue(anki.batches.all { it <= ANKI_BATCH_SIZE }, anki.batches.toString())
        assertEquals(1_000 / ANKI_BATCH_SIZE, anki.batches.size)
    }

    @Test
    fun `a failure partway reports how many landed`() {
        val anki = FakeAnkiDroid()
        anki.failWith = RuntimeException("binder died")
        anki.failAfterInserts = 2

        val result = sendToAnkiDroid(anki, words(1..1_000), report)

        assertEquals(AnkiSendResult.Failed(added = 2 * ANKI_BATCH_SIZE, attempted = 1_000), result)
        assertEquals(2 * ANKI_BATCH_SIZE, anki.notes.size)
        assertEquals(1, reports.size, reports.toString())
    }

    @Test
    fun `an unreachable provider is reported as unavailable`() {
        val anki = FakeAnkiDroid().apply { failWith = AnkiDroidException.Unavailable() }

        assertEquals(AnkiSendResult.Unavailable, sendToAnkiDroid(anki, words(1..3), report))
    }

    @Test
    fun `a refused permission is reported as such`() {
        val anki = FakeAnkiDroid().apply { failWith = AnkiDroidException.PermissionMissing() }

        assertEquals(AnkiSendResult.PermissionDenied, sendToAnkiDroid(anki, words(1..3), report))
    }

    @Test
    fun `an AnkiDroid never opened is reported as having no collection`() {
        val anki = FakeAnkiDroid().apply { failWith = AnkiDroidException.NoCollection() }

        assertEquals(AnkiSendResult.NoCollection, sendToAnkiDroid(anki, words(1..3), report))
    }

    @Test
    fun `any other failure is a failure with nothing sent and is reported`() {
        val anki = FakeAnkiDroid().apply { failWith = IllegalArgumentException("Incorrect flds argument") }

        val result = sendToAnkiDroid(anki, words(1..3), report)

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), result)
        assertTrue(anki.notes.isEmpty())
        assertEquals(1, reports.size, reports.toString())
    }
}
