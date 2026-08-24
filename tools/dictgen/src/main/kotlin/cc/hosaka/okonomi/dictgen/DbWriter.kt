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

class DbWriter(target: File) : AutoCloseable {
    private val driver: JdbcSqliteDriver
    private val db: OkonomiDb
    private var senseId = 0L

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

    fun writeRadk(data: RadkData) {
        db.transaction {
            data.radicalStrokes.forEach { (radical, strokes) -> db.kanjiQueries.insertRadical(radical, strokes) }
            data.kanjiByRadical.forEach { (radical, kanjiList) ->
                kanjiList.forEach { kanji -> db.kanjiQueries.insertKanjiRadical(kanji, radical) }
            }
        }
    }

    fun writeNames(parser: JmnedictParser) {
        db.transaction {
            parser.parse { row ->
                db.nameQueries.insertNameEntry(row.id, row.kanji, row.reading, row.nameType, row.translation)
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
        "names" to db.nameQueries.nameCount().executeAsOne(),
        "tags" to db.tagQueries.tagLabelCount().executeAsOne(),
    )

    override fun close() {
        driver.close()
    }
}
