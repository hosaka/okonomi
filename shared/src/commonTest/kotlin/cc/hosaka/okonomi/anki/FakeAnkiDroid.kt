package cc.hosaka.okonomi.anki

/**
 * AnkiDroid's provider as a send sees it, in memory. Deck names are
 * unique ignoring case and creating one twice throws, as the real
 * provider does; a note type can be created any number of times.
 *
 * [failWith] makes every call throw from then on; [failAfterInserts]
 * lets that many batches land first.
 */
internal class FakeAnkiDroid : AnkiDroidClient {
    val decks = mutableMapOf<Long, String>()
    val noteTypes = mutableMapOf<Long, String>()

    /** (note type, deck, fields) for every note that landed. */
    val notes = mutableListOf<Triple<Long, Long, List<String>>>()

    /** The size of every insert, in order. */
    val batches = mutableListOf<Int>()

    var failWith: Exception? = null
    var failAfterInserts: Int? = null
    private var nextId = 100L

    private fun check() {
        failWith?.let { if (failAfterInserts == null || batches.size >= failAfterInserts!!) throw it }
    }

    override fun findDeck(name: String): Long? {
        check()
        return decks.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.key
    }

    override fun createDeck(name: String): Long {
        check()
        require(decks.values.none { it.equals(name, ignoreCase = true) }) { "Deck name already exists: $name" }
        return nextId++.also { decks[it] = name }
    }

    override fun findNoteType(name: String): Long? {
        check()
        return noteTypes.entries.firstOrNull { it.value == name }?.key
    }

    override fun createNoteType(deckId: Long): Long {
        check()
        return nextId++.also { noteTypes[it] = AnkiNoteType.NAME }
    }

    override fun existingWords(noteTypeId: Long, words: List<String>): Set<String> {
        check()
        val present = notes.filter { it.first == noteTypeId }.map { it.third.first() }.toSet()
        return words.filter { it in present }.toSet()
    }

    override fun addNotes(noteTypeId: Long, deckId: Long, notes: List<AnkiNote>): Int {
        check()
        require(noteTypeId in noteTypes) { "note type missing: $noteTypeId" }
        batches += notes.size
        notes.forEach { this.notes += Triple(noteTypeId, deckId, it.fields) }
        return notes.size
    }
}
