package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Exercises the Phrases tab's read path over a JDBC-seeded database
 * file (see DictionaryInfoLoadTest for why JDBC).
 *
 * Seeded corpus: entry 1 (食べる) carries three sentences whose links
 * are seeded OUT of display order on purpose, so a query that leant on
 * insertion order instead of `ord` would show up here. Entry 2 is the
 * covered-by-nothing case that ~86% of the dictionary is in.
 */
class SentenceDetailTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("sentencedetail").toFile().also { tempDirs += it }

    private suspend fun seededDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        db.entryQueries.insertEntry(1L, 125L, 1L)
        db.entryQueries.insertEntry(2L, 950L, 0L)

        db.sentenceQueries.insertSentence(
            1L,
            "学校でどんな科目を勉強していますか。",
            "What subjects are you studying at school?",
            // 科目 and 勉強 state no reading in the source; dictgen took
            // theirs from the entries they resolved to, so every word
            // with kanji in it arrives here already carrying one. The
            // ids are arbitrary: nothing reads them yet, and this
            // database seeds no entries for them.
            "学校(がっこう)#101 で#102 どんな#103 科目(かもく)#104 を#105 勉強(べんきょう)#106 為る(する)#107",
        )
        db.sentenceQueries.insertSentence(
            2L,
            "早く食べる。",
            "Eat quickly.",
            // 早く is in no entry, so nothing could supply its reading.
            "早く 食べる(たべる)#108",
        )
        db.sentenceQueries.insertSentence(
            3L,
            "何を食べる。",
            "What will you eat?",
            // A malformed word between two good ones: an unbalanced
            // parenthesis nothing could have written, standing in for a
            // stored value the parser cannot make sense of.
            "何(なに)#109 食(べ 食べる(たべる)#108",
        )
        // Seeded last-first: only `ord` may decide what the reader sees.
        db.sentenceQueries.insertEntrySentence(1L, 3L, 2L)
        db.sentenceQueries.insertEntrySentence(1L, 1L, 0L)
        db.sentenceQueries.insertEntrySentence(1L, 2L, 1L)

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `hydrates an entry's sentences in the generator's display order`() = runTest {
        val sentences = seededDatabase().loadSentencesForEntry(1L)

        assertEquals(
            listOf("学校でどんな科目を勉強していますか。", "早く食べる。", "何を食べる。"),
            sentences.map { it.japanese },
        )
        assertEquals("What subjects are you studying at school?", sentences.first().english)
    }

    @Test
    fun `every kanji word carries a reading and every kana word carries none`() = runTest {
        val words = seededDatabase().loadSentencesForEntry(1L).first().words

        assertEquals(
            listOf("学校", "で", "どんな", "科目", "を", "勉強", "為る"),
            words.map { it.text },
            "the breakdown shows the dictionary form, not the inflected surface",
        )
        // This is what the breakdown exists for: a kanji word with no
        // reading would leave the line no more readable than before.
        assertEquals(
            listOf("がっこう", "かもく", "べんきょう", "する"),
            words.filter { it.reading != null }.map { it.reading },
        )
        assertEquals(
            listOf("で", "どんな", "を"),
            words.filter { it.reading == null }.map { it.text },
            "a word written in kana already reads as itself",
        )
    }

    @Test
    fun `an entry id is parsed off rather than shown`() = runTest {
        val words = seededDatabase().loadSentencesForEntry(1L)[1].words

        assertEquals(listOf("早く", "食べる"), words.map { it.text })
        assertEquals("たべる", words[1].reading)
        assertNull(
            words.first().reading,
            "a word no entry carries has no reading to take, and stays bare rather than vanishing",
        )
    }

    @Test
    fun `a malformed word is dropped without losing the rest of the breakdown`() = runTest {
        val words = seededDatabase().loadSentencesForEntry(1L)[2].words

        assertEquals(listOf("何", "食べる"), words.map { it.text })
        assertEquals("なに", words.first().reading)
    }

    @Test
    fun `an entry the corpus never uses loads empty rather than failing`() = runTest {
        assertEquals(emptyList(), seededDatabase().loadSentencesForEntry(2L))
    }

    @Test
    fun `each sentence carries its own id as its identity`() = runTest {
        // Position is not an identity: the list keys on this, and two
        // entries can show the same sentence.
        assertEquals(
            listOf(1L, 2L, 3L),
            seededDatabase().loadSentencesForEntry(1L).map { it.id },
        )
    }

    @Test
    fun `a word with an id but no reading is read as neither`() = runTest {
        val database = seededDatabase()
        database.db.sentenceQueries.insertSentence(
            4L,
            "彼女は数学が苦手だ。",
            "She is bad at maths.",
            "彼女(かのじょ)#110 は#111 数学(すうがく)#112 が#113 苦手(にがて)#114",
        )
        database.db.sentenceQueries.insertEntrySentence(2L, 4L, 0L)

        val words = database.loadSentencesForEntry(2L).single().words

        assertEquals(listOf("彼女", "は", "数学", "が", "苦手"), words.map { it.text })
        assertNull(words[1].reading, "an id must never be rendered as a reading")
        assertNull(words[3].reading)
        assertEquals(listOf("かのじょ", "すうがく", "にがて"), words.mapNotNull { it.reading })
    }
}
