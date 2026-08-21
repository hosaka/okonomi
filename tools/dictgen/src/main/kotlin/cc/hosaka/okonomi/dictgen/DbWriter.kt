package cc.hosaka.okonomi.dictgen

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.hosaka.okonomi.db.OkonomiDb
import java.io.File
import java.util.Properties

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

    private fun writeEntry(entry: JmdictEntry) {
        db.entryQueries.insertEntry(entry.id)
        entry.kanjiForms.forEachIndexed { ord, form ->
            db.entryQueries.insertKanjiForm(entry.id, ord.toLong(), form.text, form.commonRank)
        }
        entry.readings.forEachIndexed { ord, reading ->
            db.entryQueries.insertReading(
                entry.id,
                ord.toLong(),
                reading.text,
                if (reading.noKanji) 1L else 0L,
                reading.commonRank,
                reading.restrictions,
            )
        }
        entry.senses.forEachIndexed { ord, sense ->
            val id = ++senseId
            db.entryQueries.insertSense(
                id, entry.id, ord.toLong(),
                sense.pos, sense.misc, sense.field, sense.info, sense.restrictions,
            )
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
    )

    override fun close() {
        driver.close()
    }
}
