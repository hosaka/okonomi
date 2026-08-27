package cc.hosaka.okonomi.dictgen

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The B-line grammar and the disambiguation cascade, over the token
 * shapes the shipped `jpn_indices.csv` actually contains.
 */
class BLineTest {

    @Test
    fun `a bare token is a headword and nothing else`() {
        val token = BLine.parse("は").tokens.single()

        assertEquals("は", token.headword)
        assertNull(token.reading)
        assertNull(token.entrySeq)
        assertNull(token.senseIndex)
        assertNull(token.surface)
        assertTrue(!token.checked)
    }

    @Test
    fun `a reading and an inflected surface are both read off one token`() {
        val token = BLine.parse("為る(する){する}").tokens.single()

        assertEquals("為る", token.headword)
        assertEquals("する", token.reading)
        assertEquals("する", token.surface)
        assertNull(token.entrySeq)
    }

    @Test
    fun `a sense index rides beside the surface form`() {
        val token = BLine.parse("になる[01]{になりました}").tokens.single()

        assertEquals("になる", token.headword)
        assertEquals(1, token.senseIndex)
        assertEquals("になりました", token.surface)
        assertNull(token.reading)
    }

    @Test
    fun `a hash inside the parentheses is an entry sequence, not a reading`() {
        val token = BLine.parse("が(#2028930)").tokens.single()

        assertEquals("が", token.headword)
        assertEquals(2028930L, token.entrySeq)
        assertNull(token.reading, "an ent_seq must never be mistaken for a reading")
    }

    @Test
    fun `a word stating one annotation twice is rejected rather than guessed at`() {
        // Which reading does 端(はし)(はた) mean? Letting the last one win
        // is a wrong reading on screen with nothing to show for it.
        val words = BLine.parse("端(はし)(はた) 分かる")

        assertEquals(listOf("分かる"), words.tokens.map { it.headword })
        assertEquals(1, words.rejected)
        assertEquals(1, BLine.parse("為る[01][02]").rejected)
        assertEquals(1, BLine.parse("が(#1)(#2)").rejected)
    }

    @Test
    fun `the tilde marks its own word, not the sentence`() {
        val tokens = BLine.parse("ログアウト~ 為る(する){する}").tokens

        // 26,329 index lines carry the mark on some of their words and
        // only 24 on all of them, so it says nothing about the line.
        assertEquals(listOf("ログアウト", "為る"), tokens.map { it.headword })
        assertTrue(tokens[0].checked)
        assertTrue(!tokens[1].checked)
    }

    @Test
    fun `annotations are read in either order`() {
        // The one token in the shipped index that states its sense
        // before its reading. A fixed-order grammar would drop it.
        val token = BLine.parse("仮令[01](たとえ){たとえ}~").tokens.single()

        assertEquals("仮令", token.headword)
        assertEquals("たとえ", token.reading)
        assertEquals(1, token.senseIndex)
        assertEquals("たとえ", token.surface)
        assertTrue(token.checked)
    }

    @Test
    fun `a malformed token is skipped without losing the rest of the line`() {
        // Two surface forms on one token: the single malformed token in
        // the shipped index. The line's other words must survive it.
        val words = BLine.parse("此の{この} か如何か{かどうか}{かどう} 分かる")

        assertEquals(listOf("此の", "分かる"), words.tokens.map { it.headword })
        assertEquals(1, words.rejected, "the rejected word is counted, not merely dropped")
    }

    @Test
    fun `a full line reads as its words in sentence order`() {
        val tokens = BLine.parse("は 二十歳(はたち){２０歳} になる[01]{になりました}").tokens

        assertEquals(listOf("は", "二十歳", "になる"), tokens.map { it.headword })
        assertEquals(listOf(null, "はたち", null), tokens.map { it.reading })
    }
}

/**
 * The two shipped-index defects that put a real-looking word on screen
 * which finds nothing when tapped, and the boundaries of the rescue.
 *
 * Measured on the shipped index before this existed: 2,921 tokens
 * resolved to nothing AND could not be found by the search a tap opens.
 * `になる` was 2,559 of them and the `NI` marker 235 — 96% between two
 * defects, which is why the rescue is two narrow rules rather than a
 * general repair.
 */
class EntryIndexRescueTest {

    private val index = EntryIndex(
        byKanjiForm = mapOf("肉" to listOf(70L), "西遊記" to listOf(80L), "温度" to listOf(90L)),
        byReading = mapOf(
            "なる" to listOf(60L),
            "にく" to listOf(70L),
            "さいゆうき" to listOf(80L),
            "おんど" to listOf(90L),
            "に" to listOf(100L),
            // Findable ON PURPOSE: it makes にく a token that the rescue
            // WOULD rewrite (に + く, both carried) if its "already
            // findable" guard were removed. Without this the guard has
            // nothing that could catch it failing.
            "く" to listOf(110L),
            "と" to listOf(120L),
        ),
        commonRank = mapOf(
            60L to 100L, 70L to 100L, 80L to 100L, 90L to 100L, 100L to 50L, 110L to 100L, 120L to 100L,
        ),
        readings = mapOf(
            60L to listOf(IndexedReading("なる", false, emptyList())),
            70L to listOf(IndexedReading("にく", false, emptyList())),
            80L to listOf(IndexedReading("さいゆうき", false, emptyList())),
            90L to listOf(IndexedReading("おんど", false, emptyList())),
            100L to listOf(IndexedReading("に", false, emptyList())),
            110L to listOf(IndexedReading("く", false, emptyList())),
            120L to listOf(IndexedReading("と", false, emptyList())),
        ),
    )

    private fun token(headword: String, surface: String? = null) = BLineToken(
        headword = headword,
        reading = null,
        entrySeq = null,
        senseIndex = null,
        surface = surface,
        checked = false,
    )

    @Test
    fun `a word with a particle glued to its front is stored as the word`() {
        val stored = index.storedWord(token("になる", surface = "になりました"))
        assertEquals("なる", stored.headword)
        assertEquals(60L, stored.entryId)
        // The surface is the sentence's own spelling and the app scans
        // for exactly it, so rewriting the headword must not touch it.
        assertEquals("になりました", stored.surface)
    }

    @Test
    fun `a word with a particle glued to its end is stored as the word`() {
        assertEquals("温度", index.storedWord(token("温度が")).headword)
    }

    @Test
    fun `the proper noun marker is replaced by the word it stands for`() {
        val stored = index.storedWord(token("NI", surface = "西遊記"))
        assertEquals("西遊記", stored.headword)
        assertEquals(80L, stored.entryId)
    }

    @Test
    fun `the proper noun marker with no surface is left alone rather than guessed at`() {
        assertEquals("NI", index.storedWord(token("NI")).headword)
    }

    @Test
    fun `a headword the dictionary already carries is never rewritten`() {
        // にく is carried, and is ALSO に + く with both halves carried,
        // so the rescue would rewrite it to く the moment its "already
        // findable" guard stopped running. That is the whole point of
        // this test: a token that merely cannot be stripped would pass
        // with the guard deleted and prove nothing.
        val stored = index.storedWord(token("にく"))
        assertEquals("にく", stored.headword)
        assertEquals(70L, stored.entryId)
    }

    @Test
    fun `a particle is not stripped when what is left is not a word`() {
        // に + くくく is not a word, so the token stays as the source
        // wrote it rather than being redirected to something arbitrary.
        assertEquals("にくくく", index.storedWord(token("にくくく")).headword)
    }

    /**
     * にく is strippable two ways here — に leaves く and も is absent,
     * but ときも-style ambiguity is real in principle — so the order of
     * GLUED_PARTICLES decides the answer. Pinned so a reordering of that
     * list shows up as a failing test rather than as a silently
     * different generated database.
     */
    @Test
    fun `a token strippable more than one way takes the first particle listed`() {
        // とに: と leaves に (carried), に leaves と (not carried). The
        // list puts に first, so the trailing に is what goes.
        assertEquals("と", index.storedWord(token("とに")).headword)
    }

    @Test
    fun `a genuine phrase fragment is left for the search fallback`() {
        assertEquals("関する限り", index.storedWord(token("関する限り")).headword)
    }
}

class EntryIndexTest {

    /**
     * 端 is the homograph: three entries share the spelling and are told
     * apart by reading, and two of them share はし with different ranks.
     */
    private val index = EntryIndex(
        byKanjiForm = mapOf(
            "端" to listOf(10L, 11L, 12L),
            "食べる" to listOf(20L),
            "二十歳" to listOf(40L),
        ),
        byReading = mapOf(
            "はし" to listOf(10L, 11L),
            "はた" to listOf(12L),
            "たべる" to listOf(20L),
            "が" to listOf(30L),
            "にじゅっさい" to listOf(40L),
        ),
        commonRank = mapOf(
            10L to 300L, 11L to 125L, 12L to 950L, 20L to 125L, 30L to 200L, 40L to 400L,
        ),
        readings = mapOf(
            10L to listOf(reading("はし")),
            11L to listOf(reading("はし")),
            12L to listOf(reading("はた")),
            // タベル is JMdict's re_nokanji: a katakana rendering of the
            // word, not a reading of 食べる, and first in the list on
            // purpose so taking the first blindly would show it.
            20L to listOf(reading("タベル", noKanji = true), reading("たべる")),
            30L to listOf(reading("が")),
            40L to listOf(reading("にじゅっさい")),
            // The shape re_restr takes: an entry written two ways, with
            // a reading that belongs to only one of them. Taking the
            // entry's first reading in order would put it on both.
            50L to listOf(
                reading("うわて", restrictions = listOf("上手")),
                reading("じょうず"),
            ),
            // An entry all of whose readings are restricted, and to a
            // spelling the token did not use.
            60L to listOf(reading("かたぎ", restrictions = listOf("気質"))),
        ),
    )

    private fun reading(
        text: String,
        noKanji: Boolean = false,
        restrictions: List<String> = emptyList(),
    ) = IndexedReading(text, noKanji, restrictions)

    private fun token(
        headword: String,
        reading: String? = null,
        entrySeq: Long? = null,
        surface: String? = null,
    ) = BLineToken(
        headword = headword,
        reading = reading,
        entrySeq = entrySeq,
        senseIndex = null,
        surface = surface,
        checked = false,
    )

    @Test
    fun `an entry sequence settles the token outright`() {
        assertEquals(30L, index.resolve(token("が", entrySeq = 30L)))
    }

    @Test
    fun `an entry sequence wins over the headword's own candidates`() {
        // The headword would resolve elsewhere; JMdict's own number is
        // the first step of the cascade and must not be second-guessed.
        assertEquals(30L, index.resolve(token("端", entrySeq = 30L)))
    }

    @Test
    fun `an entry sequence the dictionary does not carry falls through`() {
        assertEquals(20L, index.resolve(token("食べる", entrySeq = 999L)))
    }

    @Test
    fun `a reading narrows a homograph to the entries that carry it`() {
        assertEquals(12L, index.resolve(token("端", reading = "はた")))
    }

    @Test
    fun `commonness breaks a tie the reading cannot`() {
        // Both 10 and 11 read はし; the better common_rank wins.
        assertEquals(11L, index.resolve(token("端", reading = "はし")))
    }

    @Test
    fun `an ambiguous headword with no reading falls back on commonness`() {
        assertEquals(11L, index.resolve(token("端")))
    }

    @Test
    fun `a reading that narrows to nothing leaves the candidates alone`() {
        // A reading the entry table does not associate with the headword
        // must not discard every candidate and lose the link.
        assertEquals(11L, index.resolve(token("端", reading = "みさき")))
    }

    @Test
    fun `a kana headword resolves through the reading table`() {
        assertEquals(30L, index.resolve(token("が")))
    }

    @Test
    fun `a headword the dictionary does not carry resolves to nothing`() {
        assertNull(index.resolve(token("になる")))
    }

    @Test
    fun `a kanji word with no stated reading takes the resolved entry's`() {
        // The source states a reading for only 28% of its kanji words,
        // so this is the path most of the breakdown goes through.
        val word = index.storedWord(token("食べる"))

        assertEquals("食べる", word.headword)
        assertEquals(
            "たべる",
            word.reading,
            "タベル is re_nokanji: a rendering of the word, not a reading of its kanji",
        )
        assertEquals(20L, word.entryId)
    }

    @Test
    fun `a restricted reading is only used for the form it belongs to`() {
        assertEquals("うわて", index.readingFor(50L, "上手"))
        // うわて comes first in the entry, but belongs to a spelling this
        // word is not; taking the first reading in order would put a
        // reading on it that is not one of its own.
        assertEquals("じょうず", index.readingFor(50L, "上手い"))
    }

    @Test
    fun `a headword the entry does not carry still gets a reading`() {
        // A token that resolved through its ent_seq may name a spelling
        // the entry does not list. Every reading being restricted away
        // from it leaves the entry's own first reading as a better
        // answer than none at all.
        assertEquals("かたぎ", index.readingFor(60L, "気心"))
    }

    @Test
    fun `an entry with no usable reading yields none`() {
        assertNull(index.readingFor(999L, "無い"))
    }

    @Test
    fun `a stated reading wins over the entry's primary one`() {
        // The source states a reading exactly where the entry's own
        // would be wrong for this sentence.
        val word = index.storedWord(token("二十歳", reading = "はたち"))

        assertEquals("はたち", word.reading)
        assertEquals(40L, word.entryId)
    }

    @Test
    fun `a homograph reads as the entry the cascade resolved it to`() {
        // Both spellings are 端; only the resolved entry can say which
        // reading belongs in this sentence. An arbitrary candidate
        // would read はし here half the time.
        assertEquals("はた", index.storedWord(token("端", reading = "はた")).reading)
        assertEquals(12L, index.storedWord(token("端", reading = "はた")).entryId)

        val byCommonness = index.storedWord(token("端"))
        assertEquals(11L, byCommonness.entryId)
        assertEquals("はし", byCommonness.reading)
    }

    @Test
    fun `a word with no kanji in it carries no reading`() {
        val word = index.storedWord(token("が"))

        assertEquals("が", word.headword)
        assertNull(word.reading, "a kana word reads as itself and needs nothing added")
        assertEquals(30L, word.entryId)
    }

    @Test
    fun `the surface the sentence writes comes across with the word`() {
        // Without it nothing says where 食べる sits in a sentence that
        // writes 食べない, and the reading has nowhere to go.
        val word = index.storedWord(token("食べる", surface = "食べない"))

        assertEquals("食べる", word.headword)
        assertEquals("たべる", word.reading, "the reading is the headword's, never the surface's")
        assertEquals("食べない", word.surface)
    }

    @Test
    fun `a surface equal to the headword is dropped rather than repeated`() {
        // Most of the shipped index states one; carrying it would double
        // the commonest word shape in the column for no information.
        assertNull(index.storedWord(token("食べる", surface = "食べる")).surface)
        assertNull(index.storedWord(token("食べる")).surface)
    }

    @Test
    fun `a kanji word resolving to nothing stays bare rather than being dropped`() {
        val word = index.storedWord(token("三日間"))

        assertEquals("三日間", word.headword)
        assertNull(word.reading)
        assertNull(word.entryId)
    }
}

/**
 * The writing end of the stored breakdown format.
 *
 * [CANONICAL] is the contract between dictgen and the app, which reads
 * the column back. `:shared`'s `BreakdownTest` asserts that its parser
 * PARSES this exact line into the same words this test asserts the
 * encoder PRODUCES it from — the two literals are the same string typed
 * twice, deliberately, in the spirit of `FormRowLabelsTest`.
 *
 * Without that pairing the format is defined twice and held together by
 * nothing: renaming [StoredBreakdown.ENTRY_ID_PREFIX] would leave the
 * writer and the reader disagreeing, every breakdown parsing to
 * nothing, the line quietly absent from the screen, and every test on
 * both sides green.
 */
class StoredBreakdownTest {

    private companion object {
        /**
         * Every shape the column carries: a kanji word with a reading
         * and an entry, a kana word with an entry and no reading, a
         * kanji word the source gave a reading but no entry matched, a
         * kanji word with neither, a word the sentence inflects, and one
         * the sentence spells differently without inflecting it.
         */
        const val CANONICAL =
            "学校(がっこう)#1301230 で#2028980 どんな#1009040 三日間(みっかかん) 早く " +
                "食べる(たべる){食べない}#1358280 のです{んです}#2681000"
    }

    @Test
    fun `encodes the canonical line the app parses back`() {
        val encoded = StoredBreakdown.encode(
            listOf(
                StoredBreakdownWord("学校", "がっこう", null, 1301230L),
                StoredBreakdownWord("で", null, null, 2028980L),
                StoredBreakdownWord("どんな", null, null, 1009040L),
                StoredBreakdownWord("三日間", "みっかかん", null, null),
                StoredBreakdownWord("早く", null, null, null),
                StoredBreakdownWord("食べる", "たべる", "食べない", 1358280L),
                StoredBreakdownWord("のです", null, "んです", 2681000L),
            ),
        )

        assertEquals(CANONICAL, encoded)
    }

    @Test
    fun `an empty breakdown encodes to an empty string`() {
        assertEquals("", StoredBreakdown.encode(emptyList()))
    }

    @Test
    fun `a blank reading is written as no reading at all`() {
        // `語()` would reach the reader as a bare `語 ()`.
        assertEquals("語#1", StoredBreakdown.encode(listOf(StoredBreakdownWord("語", "", null, 1L))))
        assertEquals("語#1", StoredBreakdown.encode(listOf(StoredBreakdownWord("語", " ", null, 1L))))
    }

    @Test
    fun `a surface the sentence spells like the headword is not written`() {
        // It would locate the same characters either way and cost the
        // column a copy of every uninflected word in the corpus.
        assertEquals("語#1", StoredBreakdown.encode(listOf(StoredBreakdownWord("語", null, "語", 1L))))
        assertEquals("語#1", StoredBreakdown.encode(listOf(StoredBreakdownWord("語", null, "", 1L))))
    }

    /**
     * The generator's own copy of the app's scan, which is the only
     * place the "every word is findable in its sentence" property can be
     * checked against the 978,002 words that ship: the app has the data
     * and no way to assert over it, and a CI test has neither.
     */
    @Test
    fun `counts the words a sentence can be scanned for`() {
        val words = listOf(
            StoredBreakdownWord("水", "みず", null, 1L),
            StoredBreakdownWord("を", null, null, 2L),
            StoredBreakdownWord("食べる", "たべる", "食べた", 3L),
        )

        assertEquals(3, StoredBreakdown.locate("水を食べた。", words))
        // 犬 is in no part of this sentence, and its absence costs the
        // words around it nothing.
        assertEquals(
            2,
            StoredBreakdown.locate(
                "水を飲んだ。",
                listOf(words[0], words[1], StoredBreakdownWord("犬", "いぬ", null, 4L)),
            ),
        )
    }

    @Test
    fun `a full-width surface is found in a half-width sentence`() {
        // The index writes ２月 where the sentence writes 2月. Unfolded
        // the word is lost AND its characters are left for a later
        // one-character word to claim.
        assertEquals(
            1,
            StoredBreakdown.locate("2月に生まれた。", listOf(StoredBreakdownWord("二月", "にがつ", "２月", 1L))),
        )
    }

    @Test
    fun `a delimiter inside a word fails generation rather than corrupting the column`() {
        // The encoding has no escape. No shipped headword, reading or
        // surface contains one of these, and this is what makes a future
        // source that broke that property fail loudly instead of
        // silently.
        listOf(
            StoredBreakdownWord("a b", null, null, 1L),
            StoredBreakdownWord("a(b", null, null, 1L),
            StoredBreakdownWord("a)b", null, null, 1L),
            StoredBreakdownWord("a{b", null, null, 1L),
            StoredBreakdownWord("a}b", null, null, 1L),
            StoredBreakdownWord("a#b", null, null, 1L),
            StoredBreakdownWord("語", "a b", null, 1L),
            StoredBreakdownWord("語", "a#b", null, 1L),
            StoredBreakdownWord("語", null, "a}b", 1L),
            StoredBreakdownWord("語", null, "a b", 1L),
        ).forEach { word ->
            val e = assertFailsWith<PipelineException>("$word should not encode") {
                StoredBreakdown.encode(listOf(word))
            }
            assertTrue("delimiter" in (e.message ?: ""), "message should say why: ${e.message}")
        }
    }
}

class TatoebaParserTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun parser(
        japanese: String,
        english: String,
        indices: String,
    ): TatoebaParser {
        val dir = Files.createTempDirectory("tatoeba").toFile().also { tempDirs += it }
        return TatoebaParser(
            japanese = File(dir, "jpn_sentences.tsv").apply { writeText(japanese) },
            english = File(dir, "eng_sentences.tsv").apply { writeText(english) },
            indices = File(dir, "jpn_indices.csv").apply { writeText(indices) },
        )
    }

    private fun TatoebaParser.sentences(): List<TatoebaSentence> =
        buildList { parse { add(it) } }

    private fun TatoebaParser.stats(): TatoebaStats = parse { }

    @Test
    fun `pairs both of whose sides resolve become sentences`() {
        val sentences = parser(
            japanese = "1\tjpn\t早く食べる。\n2\tjpn\t寝る。\n",
            english = "10\teng\tEat quickly.\n11\teng\tI sleep.\n",
            indices = "1\t10\t早く 食べる\n2\t11\t寝る\n",
        ).sentences()

        assertEquals(listOf("早く食べる。", "寝る。"), sentences.map { it.japanese })
        assertEquals(listOf("Eat quickly.", "I sleep."), sentences.map { it.english })
        assertEquals(listOf("早く 食べる", "寝る"), sentences.map { it.breakdown })
        assertEquals(listOf(1L, 2L), sentences.map { it.id })
    }

    @Test
    fun `a row naming a sentence that does not exist is skipped`() {
        val sentences = parser(
            japanese = "1\tjpn\t早く食べる。\n",
            english = "10\teng\tEat quickly.\n",
            // eng_id 0 means "no translation"; 404 names nothing at all.
            indices = "1\t0\t早く\n404\t10\t寝る\n1\t10\t早く 食べる\n",
        ).sentences()

        assertEquals(listOf("早く食べる。"), sentences.map { it.japanese })
        assertEquals(listOf(1L), sentences.map { it.id }, "ids are the parser's own, not Tatoeba's")
    }

    @Test
    fun `one Japanese sentence paired twice becomes two rows`() {
        // Future-proofing rather than a description of today's data:
        // 1,128 Japanese sentences appear in more than one index row,
        // but every one of those extra rows names eng_id 0 and is
        // dropped, so the shipped corpus never actually exercises this.
        // Keying on Tatoeba's id would still be wrong in principle.
        val sentences = parser(
            japanese = "1\tjpn\t寝る。\n",
            english = "10\teng\tI sleep.\n11\teng\tI go to bed.\n",
            indices = "1\t10\t寝る\n1\t11\t寝る{寝ます}\n",
        ).sentences()

        assertEquals(listOf(1L, 2L), sentences.map { it.id })
        assertEquals(listOf("I sleep.", "I go to bed."), sentences.map { it.english })
        assertEquals(listOf("寝る", "寝る{寝ます}"), sentences.map { it.breakdown })
    }

    @Test
    fun `an English sentence no index row names is ignored`() {
        val sentences = parser(
            japanese = "1\tjpn\t寝る。\n",
            english = "10\teng\tI sleep.\n99\teng\tUnreferenced.\n",
            indices = "1\t10\t寝る\n",
        ).sentences()

        assertEquals(1, sentences.size)
        assertEquals("I sleep.", sentences.single().english)
    }

    @Test
    fun `a tab inside the B-line does not truncate the row`() {
        // The index is tab-separated with the B-line last, so the split
        // has to stop at three columns rather than at every tab.
        val sentences = parser(
            japanese = "1\tjpn\t寝る。\n",
            english = "10\teng\tI sleep.\n",
            indices = "1\t10\t寝る\t余分\n",
        ).sentences()

        assertEquals("寝る\t余分", sentences.single().breakdown)
    }

    @Test
    fun `a sentence with no text is skipped rather than becoming the shortest example`() {
        // Zero characters would sort ahead of every real sentence and
        // lead the Phrases tab on every entry it touched.
        val stats = parser(
            japanese = "1\tjpn\t\n2\tjpn\t寝る。\n",
            english = "10\teng\tI sleep.\n11\teng\t\n",
            indices = "1\t10\t寝る\n2\t11\t寝る\n2\t10\t寝る\n",
        ).stats()

        assertEquals(1L, stats.pairs)
        assertEquals(2L, stats.blank)
    }

    @Test
    fun `counts every reason a row did not become a pair`() {
        val stats = parser(
            japanese = "1\tjpn\t寝る。\n",
            english = "10\teng\tI sleep.\n",
            indices = "1\t10\t寝る\n" + // kept
                "1\t0\t寝る\n" + // no translation
                "404\t10\t寝る\n" + // no such Japanese sentence
                "1\t999\t寝る\n" + // no such English sentence
                "1,10,寝る\n", // not tab separated at all
        ).stats()

        assertEquals(4L, stats.rows, "a row that will not split into columns is not a row")
        assertEquals(1L, stats.pairs)
        assertEquals(1L, stats.malformedRows)
        assertEquals(1L, stats.untranslated)
        assertEquals(1L, stats.missingJapanese)
        assertEquals(1L, stats.missingEnglish)
        assertEquals(3L, stats.skipped)
    }
}
