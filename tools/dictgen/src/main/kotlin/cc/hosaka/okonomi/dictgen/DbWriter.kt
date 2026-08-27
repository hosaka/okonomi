package cc.hosaka.okonomi.dictgen

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.hosaka.okonomi.db.OkonomiDb
import java.io.File
import java.util.Properties

/**
 * Highest gloss count one sense may carry. The search orders English
 * results by `sense.ord * GLOSS_POSITION_FACTOR + gloss.ord`, which
 * only stays sortable while every sense fits below this.
 */
const val GLOSS_POSITION_FACTOR = 1000

/**
 * How much of the breakdown must be findable in its own sentences before
 * generation is allowed to succeed. See `checkWordsAreLocatable`.
 */
const val MINIMUM_LOCATED_PERCENT = 90.0

/** Fewer stored words than this is a fixture, not a corpus. */
const val MINIMUM_LOCATED_SAMPLE = 1_000L

class DbWriter(target: File) : AutoCloseable {
    private val driver: JdbcSqliteDriver
    private val db: OkonomiDb
    private var senseId = 0L
    private var tatoeba: TatoebaStats? = null
    private var rejectedWords = 0L
    private var storedWords = 0L
    private var locatedWords = 0L
    private var droppedNames = 0L

    init {
        target.parentFile?.mkdirs()
        target.delete()
        // Generation-only pragmas; the shipped file is opened read-only by the app.
        val properties = Properties().apply {
            setProperty("journal_mode", "MEMORY")
            setProperty("synchronous", "OFF")
        }
        driver = JdbcSqliteDriver("jdbc:sqlite:${target.absolutePath}", properties)
        OkonomiDb.Schema.create(driver)
        db = OkonomiDb(driver)
    }

    fun writeJmdict(parser: JmdictParser) {
        db.transaction {
            parser.parse { entry -> writeEntry(entry) }
        }
    }

    /** Parser-free entry point, for tests that build entries directly. */
    fun writeJmdictEntries(entries: List<JmdictEntry>) {
        db.transaction {
            entries.forEach { entry -> writeEntry(entry) }
        }
    }

    private fun writeEntry(entry: JmdictEntry) {
        // Entry-level commonness is denormalized from the forms so the
        // search's FTS pre-ranking can order by it before truncating.
        val ranks = entry.kanjiForms.map { it.commonRank } + entry.readings.map { it.commonRank }
        val isCommon = entry.kanjiForms.any { it.isCommon } || entry.readings.any { it.isCommon }
        db.entryQueries.insertEntry(
            entry.id,
            ranks.minOrNull() ?: PriorityRank.rank(emptyList()),
            if (isCommon) 1L else 0L,
        )
        entry.kanjiForms.forEachIndexed { ord, form ->
            db.entryQueries.insertKanjiForm(
                entry.id, ord.toLong(), form.text, form.commonRank, if (form.isCommon) 1L else 0L,
            )
        }
        entry.readings.forEachIndexed { ord, reading ->
            db.entryQueries.insertReading(
                entry.id,
                ord.toLong(),
                reading.text,
                if (reading.noKanji) 1L else 0L,
                reading.commonRank,
                reading.restrictions,
                if (reading.isCommon) 1L else 0L,
            )
        }
        entry.senses.forEachIndexed { ord, sense ->
            val id = ++senseId
            db.entryQueries.insertSense(
                id, entry.id, ord.toLong(),
                sense.pos, sense.misc, sense.field, sense.dial, sense.info, sense.restrictions,
            )
            if (sense.glosses.size >= GLOSS_POSITION_FACTOR) {
                // The search packs (sense ord, gloss ord) into
                // sense.ord * GLOSS_POSITION_FACTOR + gloss.ord; a
                // sense this large would bleed into the next sense's
                // range and silently corrupt result ordering.
                throw PipelineException(
                    "Entry ${entry.id} sense $ord has ${sense.glosses.size} glosses, " +
                        "at or beyond the $GLOSS_POSITION_FACTOR the search's position packing allows.",
                )
            }
            sense.glosses.forEachIndexed { glossOrd, gloss ->
                db.entryQueries.insertGloss(id, glossOrd.toLong(), gloss)
            }
        }
        val seen = HashSet<Int>()
        for (form in entry.kanjiForms) {
            form.text.codePoints().forEach { cp ->
                if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN && seen.add(cp)) {
                    db.entryQueries.insertEntryKanji(String(Character.toChars(cp)), entry.id)
                }
            }
        }
    }

    /**
     * Writes the Tatoeba pairs and the links from entries to them. Must
     * run after [writeJmdict]: the linker resolves B-line headwords
     * against the entries already in the file, so an id it hands back
     * always exists.
     *
     * The cap cannot be applied while streaming — it is global per
     * entry, and the tenth-shortest sentence for 行く is not knowable
     * until all 4,606 of its candidates have been seen — so links are
     * collected first, capped and ordered second, and the sentences no
     * surviving link references are pruned last.
     */
    fun writeTatoeba(parser: TatoebaParser) {
        val index = entryIndex()
        val links = HashMap<Long, TopSentences>()
        var stats: TatoebaStats? = null
        db.transaction {
            stats = parser.parse { sentence ->
                val words = BLine.parse(sentence.breakdown)
                rejectedWords += words.rejected
                // Resolved once per word: the entry id the breakdown
                // stores is the same one the link is built from.
                val stored = words.tokens.map { index.storedWord(it) }
                // Counted before the row is written: the app finds a
                // word in the sentence by scanning for its surface, and
                // a format or source change that broke that would leave
                // every sentence on screen bare with nothing failing.
                storedWords += stored.size
                locatedWords += StoredBreakdown.locate(sentence.japanese, stored)
                // The B-line is rewritten rather than stored: every
                // kanji word leaves here with a reading, taken from the
                // entry it resolved to where the source states none.
                db.sentenceQueries.insertSentence(
                    sentence.id,
                    sentence.japanese,
                    sentence.english,
                    StoredBreakdown.encode(stored),
                )
                // A sentence naming one entry twice is one link, and the
                // tilde counts for an entry only when it sits on a word
                // that resolved to that entry: the mark is per word, and
                // 26,329 lines carry it on some of their words against
                // 24 that carry it on all.
                val checkedByEntry = LinkedHashMap<Long, Boolean>()
                words.tokens.forEachIndexed { position, token ->
                    val entryId = stored[position].entryId ?: return@forEachIndexed
                    checkedByEntry[entryId] = checkedByEntry[entryId] == true || token.checked
                }
                val key = SentenceKey.of(sentence.japanese)
                checkedByEntry.forEach { (entryId, checked) ->
                    links.getOrPut(entryId) { TopSentences(SENTENCES_PER_ENTRY) }.offer(
                        SentenceLink(
                            sentenceId = sentence.id,
                            length = sentence.japanese.length,
                            checked = checked,
                            key = key,
                        ),
                    )
                }
            }
        }
        db.transaction {
            // Sorted keys, so the generated file does not depend on hash
            // iteration order from one run to the next.
            links.keys.sorted().forEach { entryId ->
                links.getValue(entryId).ordered().forEachIndexed { ord, link ->
                    db.sentenceQueries.insertEntrySentence(entryId, link.sentenceId, ord.toLong())
                }
            }
        }
        db.transaction {
            db.sentenceQueries.deleteUnlinkedSentences()
        }
        tatoeba = checkNotNull(stats) { "the parser must report what it read" }
        checkTatoebaProducedSomething(tatoeba!!)
    }

    /**
     * Fails generation when the sentence sources produced nothing
     * usable.
     *
     * Every step here degrades quietly by design — an unreadable row is
     * skipped, an unresolvable word is skipped — which is right per row
     * and wrong in bulk. If Tatoeba ever shipped `jpn_indices.csv`
     * genuinely comma-separated, as its extension invites, every row
     * would skip, the build would succeed, and the Phrases tab would be
     * empty on every entry with nothing anywhere saying why.
     */
    private fun checkTatoebaProducedSomething(stats: TatoebaStats) {
        if (stats.rows > 0 && stats.pairs == 0L) {
            throw PipelineException(
                "Read ${stats.rows} sentence index rows and resolved none of them to a pair " +
                    "(${stats.malformedRows} could not be split into three tab-separated columns). " +
                    "The sentence sources are present but unreadable.",
            )
        }
        val linked = db.sentenceQueries.entrySentenceCount().executeAsOne()
        if (stats.pairs > 0 && linked == 0L) {
            throw PipelineException(
                "Resolved ${stats.pairs} sentence pairs but linked none of them to an entry. " +
                    "The B-line grammar or the entry index is broken, not the sources.",
            )
        }
        checkWordsAreLocatable()
    }

    /**
     * Fails generation when the stored words stop being findable in the
     * sentences they belong to.
     *
     * Every reading and every tap target on the Phrases tab hangs off
     * that scan, and it degrades per word by design, so a source that
     * stopped stating surfaces — or a stored format that stopped
     * carrying them — would ship a database whose sentences render as
     * bare text, with the schema fingerprint unmoved and every other
     * check green. The shipped data locates 99.996% of its words; the
     * floor is far below that because this is a broken-pipeline alarm,
     * not a quality gate.
     *
     * Silent below [MINIMUM_LOCATED_SAMPLE] words, and that is not a
     * loophole: a hand-built fixture of two words says nothing about a
     * pipeline, and an alarm that cries wolf on every unit test is an
     * alarm someone deletes. A real run is three orders of magnitude
     * above the bound.
     */
    private fun checkWordsAreLocatable() {
        if (storedWords < MINIMUM_LOCATED_SAMPLE) return
        val located = locatedWords * 100.0 / storedWords
        if (located < MINIMUM_LOCATED_PERCENT) {
            throw PipelineException(
                "Only %.2f%% of the %,d stored breakdown words can be found in their own sentences ".format(
                    located,
                    storedWords,
                ) + "(expected above $MINIMUM_LOCATED_PERCENT%). The Phrases tab locates a word by " +
                    "scanning the sentence for its surface form, so it would render these sentences " +
                    "with no readings and nothing to tap.",
            )
        }
    }

    /**
     * The (text -> entry) mapping the linker resolves headwords
     * against, read back from the entries just written rather than
     * accumulated during [writeJmdict]: the file is the single source of
     * truth for what an entry id means, and a second in-memory copy
     * built on the way in could only drift from it.
     */
    private fun entryIndex(): EntryIndex {
        val byKanjiForm = HashMap<String, MutableList<Long>>()
        db.entryQueries.allKanjiForms().executeAsList().forEach { row ->
            byKanjiForm.getOrPut(row.text) { mutableListOf() } += row.entry_id
        }
        val byReading = HashMap<String, MutableList<Long>>()
        // The query is ordered by (entry, ord), so each entry's
        // readings arrive in JMdict's own order and the breakdown can
        // take the first one that fits the written form it matched.
        val readings = HashMap<Long, MutableList<IndexedReading>>()
        db.entryQueries.allReadings().executeAsList().forEach { row ->
            byReading.getOrPut(row.text) { mutableListOf() } += row.entry_id
            readings.getOrPut(row.entry_id) { mutableListOf() } += IndexedReading(
                text = row.text,
                noKanji = row.no_kanji != 0L,
                restrictions = row.restrictions
                    ?.split(StoredFormat.RESTRICTIONS)
                    ?.filter { it.isNotEmpty() }
                    .orEmpty(),
            )
        }
        val commonRank = db.entryQueries.allEntryRanks().executeAsList()
            .associate { it.id to it.common_rank }
        return EntryIndex(byKanjiForm, byReading, commonRank, readings)
    }

    fun writeKanjidic(parser: KanjidicParser) {
        db.transaction {
            parser.parse { character ->
                db.kanjiQueries.insertKanji(
                    character.literal, character.grade, character.strokeCount, character.freq, character.jlpt,
                )
                character.onReadings.forEach { db.kanjiQueries.insertKanjiReading(character.literal, "on", it) }
                character.kunReadings.forEach { db.kanjiQueries.insertKanjiReading(character.literal, "kun", it) }
                character.nanori.forEach { db.kanjiQueries.insertKanjiReading(character.literal, "nanori", it) }
                character.meanings.forEachIndexed { ord, meaning ->
                    db.kanjiQueries.insertKanjiMeaning(character.literal, ord.toLong(), meaning)
                }
            }
        }
    }

    /**
     * One row per character, its strokes joined by [StoredFormat.STROKE_PATHS]
     * in KanjiVG's drawing order. The join is what keeps stroke order out of
     * row order; see the note on `kanji_stroke_order` in kanji.sq.
     */
    fun writeKanjivg(parser: KanjivgParser) {
        db.transaction {
            parser.parse { character ->
                db.kanjiQueries.insertKanjiStrokeOrder(
                    character.literal,
                    character.paths.joinToString(StoredFormat.STROKE_PATHS),
                )
            }
        }
    }

    fun writeRadk(data: RadkData) {
        db.transaction {
            data.radicalStrokes.forEach { (radical, strokes) -> db.kanjiQueries.insertRadical(radical, strokes) }
            data.kanjiByRadical.forEach { (radical, kanjiList) ->
                kanjiList.forEach { kanji -> db.kanjiQueries.insertKanjiRadical(kanji, radical) }
            }
        }
    }

    /**
     * Writes the JMnedict rows the app can use, which is the person names
     * alone: see [isPersonNameType] for what that means and why. The
     * dropped count is reported beside the kept one rather than discarded,
     * because the filter is the whole point of reading this source and a
     * drop count that collapsed to zero (a renamed entity, say) would
     * otherwise ship a third of a gigabyte of places again in silence.
     */
    fun writeNames(parser: JmnedictParser) {
        db.transaction {
            parser.parse { row ->
                if (isPersonNameType(row.nameType)) {
                    db.nameQueries.insertNameEntry(row.id, row.kanji, row.reading, row.nameType, row.translation)
                } else {
                    droppedNames++
                }
            }
        }
    }

    /**
     * Writes the labels the app shows for the short codes stored on
     * senses and name entries. [entities] is the union of the source
     * DTDs' entity declarations, written in the source's own casing:
     * showing them lowercase is a presentation choice the entry view
     * makes, and baking it into the file would cost a format bump and a
     * full re-copy on every device to undo.
     *
     * Empty input is a programming error, not a quiet degradation: it
     * means the parsers had not read their DTDs yet, and every chip in
     * the app would silently fall back to a raw code.
     */
    fun writeTagLabels(entities: Map<String, String>) {
        check(entities.isNotEmpty()) {
            "No DTD entities to write: writeTagLabels must run after the parsers have read their sources."
        }
        db.transaction {
            entities.forEach { (code, expansion) ->
                db.tagQueries.insertTagLabel(code, expansion)
            }
        }
    }

    fun finish(metadata: Map<String, String>) {
        db.transaction {
            metadata.forEach { (key, value) -> db.metadataQueries.insertMetadata(key, value) }
        }
        // VACUUM may renumber implicit rowids of gloss, so the FTS index is built strictly after it.
        // The FTS 'rebuild' insert does not compile in .sq files, so it runs as raw SQL.
        driver.execute(null, "VACUUM", 0)
        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0)
        driver.execute(null, "ANALYZE", 0)
        // Baking the schema version into the file lets the app-side driver skip
        // Schema.create/migrate: it only creates when user_version is 0.
        driver.execute(null, "PRAGMA user_version = ${OkonomiDb.Schema.version}", 0)
    }

    fun counts(): Map<String, Long> = mapOf(
        "entries" to db.entryQueries.entryCount().executeAsOne(),
        "glosses" to db.entryQueries.glossCount().executeAsOne(),
        "kanji" to db.kanjiQueries.kanjiCount().executeAsOne(),
        "radicals" to db.kanjiQueries.radicalCount().executeAsOne(),
        // Characters that have a stroke-order diagram, NOT a stroke
        // total: the table holds one row per character. Printed beside
        // "kanji" a label of "strokes" would read as a stroke count that
        // had come up short.
        "diagrams" to db.kanjiQueries.kanjiStrokeOrderCount().executeAsOne(),
        // Person names kept, and everything else JMnedict offered.
        "names" to db.nameQueries.nameCount().executeAsOne(),
        "nonperson" to droppedNames,
        "tags" to db.tagQueries.tagLabelCount().executeAsOne(),
        "sentences" to db.sentenceQueries.sentenceCount().executeAsOne(),
        "links" to db.sentenceQueries.entrySentenceCount().executeAsOne(),
        // Not table sizes but the two quiet failure modes: index rows
        // that never became a pair, and B-line words the grammar
        // rejected. Both are near zero in the shipped sources, so a
        // number that moves is the first sign a source changed shape.
        "skipped" to (tatoeba?.skipped ?: 0L),
        "rejected" to rejectedWords,
        // The Phrases tab's own quiet failure: a stored word the app
        // cannot find in its sentence contributes no reading and no tap.
        // Near zero in the shipped sources, so a number that moves is
        // the first sign the surfaces stopped arriving.
        "unplaced" to (storedWords - locatedWords),
    )

    override fun close() {
        driver.close()
    }
}
