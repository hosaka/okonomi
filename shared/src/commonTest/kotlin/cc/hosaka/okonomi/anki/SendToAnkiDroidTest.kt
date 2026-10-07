package cc.hosaka.okonomi.anki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendToAnkiDroidTest {

    private val reports = mutableListOf<String>()
    private val report: (String, Throwable?) -> Unit = { message, _ -> reports += message }

    private fun words(range: IntRange) =
        range.map { AnkiNote(word = "word$it", reading = "r$it", meaning = "m$it", entryId = 1_000_000L + it) }

    private fun send(anki: FakeAnkiDroid, notes: List<AnkiNote>) =
        sendToAnkiDroid(anki, notes, report, linkLabel = "More in Okonomi")

    @Test
    fun `a first send creates the deck and the note type and adds every word`() {
        val anki = FakeAnkiDroid()

        val result = send(anki, words(1..10))

        assertEquals(AnkiSendResult.Sent(added = 10, alreadyThere = 0), result)
        assertEquals(listOf(ANKI_DECK_NAME), anki.decks.values.toList())
        assertEquals(listOf(AnkiNoteType.NAME to AnkiNoteType.fields), anki.noteTypes.values.toList())
        val deckId = anki.decks.keys.single()
        val noteTypeId = anki.noteTypes.keys.single()
        assertEquals(words(1..10).map { Triple(noteTypeId, deckId, it.fields) }, anki.notes)
    }

    @Test
    fun `the note type makes a forward and a reverse card from each note`() {
        val anki = FakeAnkiDroid()
        send(anki, words(1..1))
        val (forward, reverse) = anki.templates.values.single()

        assertEquals("<span lang=\"ja\">{{Word}}</span>", forward.front)
        assertTrue("<span lang=\"ja\">{{Reading}}</span>" in reverse.front, reverse.front)
        assertTrue("<span lang=\"ja\">{{Word}}</span>" in reverse.back, reverse.back)
        assertTrue("<span lang=\"ja\">{{Reading}}</span>" in forward.back, forward.back)
        assertTrue("{{Reading}}" in forward.back && "{{Meaning}}" in forward.back, forward.back)
        assertTrue("{{Reading}}" in reverse.front && "{{Meaning}}" in reverse.front, reverse.front)
        assertTrue("{{Word}}" !in reverse.front, reverse.front)
        assertTrue("{{Word}}" in reverse.back, reverse.back)
    }

    @Test
    fun `both backs end in the link to the entry and no front carries it`() {
        val anki = FakeAnkiDroid()
        sendToAnkiDroid(anki, words(1..1), report, linkLabel = "More & <more>")
        val created = anki.templates.values.single()
        val link = "<a href=\"okonomi://entry/{{EntryId}}\">More &amp; &lt;more&gt;</a>"

        created.forEach { template ->
            assertTrue(template.back.endsWith(link), template.back)
            assertTrue("EntryId" !in template.front, template.front)
            // Only inside the link: EntryId is never shown as text.
            assertEquals(1, Regex("\\{\\{EntryId}}").findAll(template.back).count(), template.back)
        }
        val forward = created.first().back
        assertTrue(forward.indexOf("{{Meaning}}") < forward.indexOf(link), "the link sits below the meaning: $forward")
    }

    @Test
    fun `the card style leaves colours to Anki so night mode works`() {
        assertTrue("color" !in AnkiNoteType.CSS, AnkiNoteType.CSS)
    }

    @Test
    fun `a batch that lands short ends the send as a partial failure and never as sent`() {
        val anki = FakeAnkiDroid().apply { landShort = ANKI_BATCH_SIZE - 1 }

        val result = send(anki, words(1..1_000))

        assertEquals(AnkiSendResult.Failed(added = ANKI_BATCH_SIZE - 1, attempted = 1_000), result)
        assertEquals(listOf(ANKI_BATCH_SIZE), anki.batches, "no batch is sent after a short one")
        assertEquals(1, reports.size, reports.toString())
    }

    @Test
    fun `an insert that lands nothing is a failure with nothing sent`() {
        // ContentResolver answers 0 when AnkiDroid's process dies under the call.
        val anki = FakeAnkiDroid().apply { landShort = 0 }

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 3), send(anki, words(1..3)))
    }

    @Test
    fun `a note type of our name with other fields is a plain failure and is left alone`() {
        val anki = FakeAnkiDroid()
        val old = anki.addNoteType(AnkiNoteType.NAME, listOf("Word", "Reading", "Meaning"), listOf("Forward", "Reverse"))

        val result = send(anki, words(1..3))

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), result)
        assertTrue(anki.notes.isEmpty())
        assertEquals(setOf(old), anki.noteTypes.keys)
        assertEquals(1, reports.size, reports.toString())
    }

    @Test
    fun `a half-built note type with placeholder cards is never reused`() {
        val anki = FakeAnkiDroid()
        anki.addNoteType(AnkiNoteType.NAME, AnkiNoteType.fields, listOf("Card 1", "Card 2"))

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), send(anki, words(1..3)))
        assertTrue(anki.notes.isEmpty())
    }

    @Test
    fun `a note type whose creation fails ends the send as a failure`() {
        val anki = object : AnkiDroidClient by FakeAnkiDroid() {
            override fun createNoteType(deckId: Long, templates: List<AnkiCardTemplate>): Long =
                error("AnkiDroid did not write card template 1")
        }

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), sendToAnkiDroid(anki, words(1..3), report, "x"))
        assertEquals(1, reports.size, reports.toString())
    }

    @Test
    fun `the same entry twice in one send is sent once`() {
        val anki = FakeAnkiDroid()

        val result = send(anki, words(1..2) + words(1..1))

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), result)
        assertEquals(2, anki.notes.size)
    }

    @Test
    fun `two entries spelled alike are both sent and neither is sent again`() {
        val anki = FakeAnkiDroid()
        val nama = listOf(
            AnkiNote(word = "生", reading = "なま", meaning = "- raw", entryId = 1_421_850L),
            AnkiNote(word = "生", reading = "せい", meaning = "- life", entryId = 1_421_870L),
        )

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), send(anki, nama))
        assertEquals(AnkiSendResult.NothingNew, send(anki, nama))
        assertEquals(listOf("1421850", "1421870"), anki.notes.map { it.third[3] })
    }

    @Test
    fun `a re-send adds only the words not already there and reuses the deck and note type`() {
        val anki = FakeAnkiDroid()
        send(anki, words(1..10))

        val result = send(anki, words(1..13))

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
        val note = AnkiNote(word = "寝る", reading = "ねる", meaning = ankiFieldHtml(ankiMeaning(glosses)), entryId = 1L)

        send(anki, listOf(note))

        val meaning = anki.notes.single().third[2]
        assertEquals(glosses.take(5).map { "- $it" }, meaning.split("<br>"))
        assertTrue("…" !in meaning, meaning)
    }

    @Test
    fun `nothing new to send adds nothing`() {
        val anki = FakeAnkiDroid()
        send(anki, words(1..5))

        val result = send(anki, words(1..5))

        assertEquals(AnkiSendResult.NothingNew, result)
        assertEquals(5, anki.notes.size)
        assertEquals(listOf(5), anki.batches)
    }

    @Test
    fun `a deck the reader deleted is made again`() {
        val anki = FakeAnkiDroid()
        send(anki, words(1..2))
        anki.decks.clear()

        val result = send(anki, words(3..4))

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), result)
        val deckId = anki.decks.entries.single { it.value == ANKI_DECK_NAME }.key
        assertEquals(listOf(deckId, deckId), anki.notes.drop(2).map { it.second })
    }

    @Test
    fun `a note type the reader deleted is made again and its words are sent afresh`() {
        val anki = FakeAnkiDroid()
        send(anki, words(1..2))
        // Deleting a note type in Anki deletes its notes with it.
        anki.noteTypes.clear()
        anki.notes.clear()

        val result = send(anki, words(1..2))

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), result)
        assertEquals(1, anki.noteTypes.size)
    }

    @Test
    fun `a deck that differs only in case is the same deck`() {
        val anki = FakeAnkiDroid()
        anki.decks[1L] = ANKI_DECK_NAME.lowercase()

        val result = send(anki, words(1..1))

        assertEquals(AnkiSendResult.Sent(added = 1, alreadyThere = 0), result)
        assertEquals(listOf(1L), anki.notes.map { it.second })
    }

    @Test
    fun `a thousand words go in batches no larger than the limit and all land`() {
        val anki = FakeAnkiDroid()

        val result = send(anki, words(1..1_000))

        assertEquals(AnkiSendResult.Sent(added = 1_000, alreadyThere = 0), result)
        assertEquals(1_000, anki.batches.sum())
        assertTrue(anki.batches.all { it <= ANKI_BATCH_SIZE }, anki.batches.toString())
        assertEquals((1_000 + ANKI_BATCH_SIZE - 1) / ANKI_BATCH_SIZE, anki.batches.size)
    }

    @Test
    fun `a failure partway reports how many landed`() {
        val anki = FakeAnkiDroid()
        anki.failWith = RuntimeException("binder died")
        anki.failAfterInserts = 2

        val result = send(anki, words(1..1_000))

        assertEquals(AnkiSendResult.Failed(added = 2 * ANKI_BATCH_SIZE, attempted = 1_000), result)
        assertEquals(2 * ANKI_BATCH_SIZE, anki.notes.size)
        assertEquals(1, reports.size, reports.toString())
    }

    @Test
    fun `an unreachable provider is reported as unavailable`() {
        val anki = FakeAnkiDroid().apply { failWith = AnkiDroidException.Unavailable() }

        assertEquals(AnkiSendResult.Unavailable, send(anki, words(1..3)))
    }

    @Test
    fun `a refused permission is reported as such`() {
        val anki = FakeAnkiDroid().apply { failWith = AnkiDroidException.PermissionMissing() }

        assertEquals(AnkiSendResult.PermissionDenied(), send(anki, words(1..3)))
    }

    @Test
    fun `an AnkiDroid never opened is reported as having no collection`() {
        val anki = FakeAnkiDroid().apply { failWith = AnkiDroidException.NoCollection() }

        assertEquals(AnkiSendResult.NoCollection, send(anki, words(1..3)))
    }

    @Test
    fun `any other failure is a failure with nothing sent and is reported`() {
        val anki = FakeAnkiDroid().apply { failWith = IllegalArgumentException("Incorrect flds argument") }

        val result = send(anki, words(1..3))

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), result)
        assertTrue(anki.notes.isEmpty())
        assertEquals(1, reports.size, reports.toString())
    }
}
