package cc.hosaka.okonomi.anki

/**
 * AnkiDroid's provider as a send sees it, in memory. Deck names are
 * unique ignoring case and creating one twice throws, as the real
 * provider does; a note type can be created any number of times, and an
 * insert whose field count is not the note type's throws.
 *
 * [failWith] makes every call throw from then on; [failAfterInserts]
 * lets that many batches land first. [landShort] makes every insert
 * land at most that many of its notes, as a provider does whose process
 * dies under the call.
 */
internal class FakeAnkiDroid : AnkiDroidClient {
    val decks = mutableMapOf<Long, String>()

    /** Name and field names of every note type. */
    val noteTypes = mutableMapOf<Long, Pair<String, List<String>>>()

    /** Template names of every note type. */
    val templateNames = mutableMapOf<Long, List<String>>()

    /** The templates each note type was created with. */
    val templates = mutableMapOf<Long, List<AnkiCardTemplate>>()

    /** (note type, deck, fields) for every note that landed. */
    val notes = mutableListOf<Triple<Long, Long, List<String>>>()

    /** The size of every insert, in order. */
    val batches = mutableListOf<Int>()

    var failWith: Exception? = null
    var failAfterInserts: Int? = null
    var landShort: Int? = null
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

    /** A note type of [name] as something other than this send left it. */
    fun addNoteType(name: String, fields: List<String>, templateNames: List<String>): Long =
        nextId++.also {
            noteTypes[it] = name to fields
            this.templateNames[it] = templateNames
        }

    override fun findNoteType(name: String): FoundNoteType? {
        check()
        return noteTypes.entries.firstOrNull { it.value.first == name }
            ?.let { FoundNoteType(it.key, it.value.second, templateNames[it.key].orEmpty()) }
    }

    override fun createNoteType(deckId: Long, templates: List<AnkiCardTemplate>): Long {
        check()
        return addNoteType(AnkiNoteType.NAME, AnkiNoteType.fields, templates.map { it.name })
            .also { this.templates[it] = templates }
    }

    override fun existingEntryIds(noteTypeId: Long, entryIds: List<Long>): Set<Long> {
        check()
        val present = notes.filter { it.first == noteTypeId }.mapNotNull { it.third.getOrNull(3)?.toLongOrNull() }.toSet()
        return entryIds.filter { it in present }.toSet()
    }

    override fun addNotes(noteTypeId: Long, deckId: Long, notes: List<AnkiNote>): Int {
        check()
        val noteType = requireNotNull(noteTypes[noteTypeId]) { "note type missing: $noteTypeId" }
        notes.forEach { require(it.fields.size == noteType.second.size) { "Incorrect flds argument" } }
        val landing = notes.take(landShort ?: notes.size)
        batches += notes.size
        landing.forEach { this.notes += Triple(noteTypeId, deckId, it.fields) }
        return landing.size
    }
}
