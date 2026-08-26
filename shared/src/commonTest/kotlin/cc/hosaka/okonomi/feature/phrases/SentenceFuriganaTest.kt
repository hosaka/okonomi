package cc.hosaka.okonomi.feature.phrases

import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.ui.furigana.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sentence as the tab draws it.
 *
 * Two failures have no other test. The first is a sentence that is not
 * the sentence: rebuilding the line from the breakdown's headwords
 * would render もっと果物を食べるべきです。 as …食べる可きです, because 可き
 * is what the dictionary calls べき — a sentence Tatoeba does not hold
 * and nobody wrote. The second is silence: readings resolved at build
 * time, carried through the column and never drawn, with the pipeline's
 * tests and the loader's tests all green.
 */
class SentenceFuriganaTest {

    private companion object {
        const val KURU = 1_547_720L
        const val IKU = 1_578_850L
        const val TABERU = 1_358_280L
    }

    private fun sentence(japanese: String, vararg words: BreakdownWord) = ExampleSentence(
        id = 1L,
        japanese = japanese,
        english = "translation",
        words = words.toList(),
    )

    private fun rendered(
        sentence: ExampleSentence,
        entryPos: Map<Long, List<String>> = emptyMap(),
    ): String =
        sentencePieces(sentence, entryPos).joinToString("") { piece ->
            piece.segments.joinToString("") { segment ->
                segment.reading?.let { "[${segment.text}[$it]]" } ?: segment.text
            }
        }

    /** The example the spec states an expected rendering for, in full. */
    @Test
    fun `an ordinary sentence takes its readings over the words that have them`() {
        val rendered = rendered(
            sentence(
                "もっと果物を食べるべきです。",
                BreakdownWord("もっと", null),
                BreakdownWord("果物", "くだもの"),
                BreakdownWord("を", null),
                BreakdownWord("食べる", "たべる"),
                // The dictionary spells べき as 可き. The sentence does
                // not, and the sentence wins.
                BreakdownWord("可き", "べき", surface = "べき"),
                BreakdownWord("です", null),
            ),
        )

        assertEquals("もっと[果物[くだもの]]を[食[た]]べるべきです。", rendered)
    }

    @Test
    fun `an inflected word carries its stem's reading`() {
        assertEquals(
            "[父[ちち]]は[果物[くだもの]]を[食[た]]べないんです。",
            rendered(
                sentence(
                    "父は果物を食べないんです。",
                    BreakdownWord("父", "ちち"),
                    BreakdownWord("は", null),
                    BreakdownWord("果物", "くだもの"),
                    BreakdownWord("を", null),
                    BreakdownWord("食べる", "たべる", surface = "食べない"),
                    BreakdownWord("のです", null, surface = "んです"),
                ),
            ),
        )
    }

    @Test
    fun `a word whose reading will not transfer keeps its span and takes no ruby`() {
        val pieces = sentencePieces(
            sentence(
                "彼は２０歳です。",
                BreakdownWord("彼", "かれ"),
                BreakdownWord("は", null),
                BreakdownWord("二十歳", "はたち", surface = "２０歳"),
                BreakdownWord("です", null),
            ),
        )
        val age = pieces.single { it.word?.text == "二十歳" }

        assertEquals("２０歳", age.segments.plainText())
        assertTrue(age.segments.none { it.reading != null }, "a reading here would be one nobody stated")
    }

    @Test
    fun `a word the sentence does not contain leaves the sentence whole`() {
        assertEquals(
            "[水[みず]]を[飲[の]]む。",
            rendered(
                sentence(
                    "水を飲む。",
                    BreakdownWord("水", "みず"),
                    BreakdownWord("を", null),
                    // An index row naming a word this sentence lacks.
                    BreakdownWord("犬", "いぬ"),
                    BreakdownWord("飲む", "のむ", surface = "飲む"),
                ),
            ),
        )
    }

    @Test
    fun `a sentence with no words at all is one plain piece`() {
        val pieces = sentencePieces(sentence("ふうん。"))

        assertEquals("ふうん。", pieces.single().segments.plainText())
        assertNull(pieces.single().word, "there is nothing here to tap")
    }

    @Test
    fun `the same word twice is two pieces and so two tap targets`() {
        val pieces = sentencePieces(
            sentence(
                "食べる食べる。",
                BreakdownWord("食べる", "たべる"),
                BreakdownWord("食べる", "たべる"),
            ),
        )

        assertEquals(3, pieces.size, "two words and the full stop")
        assertEquals(listOf("食べる", "食べる", "。"), pieces.map { it.segments.plainText() })
    }

    @Test
    fun `punctuation and unclaimed text belong to no word`() {
        val pieces = sentencePieces(
            sentence(
                "水を飲む。",
                BreakdownWord("水", "みず"),
                BreakdownWord("飲む", "のむ"),
            ),
        )

        assertEquals(
            listOf("水" to "水", "を" to null, "飲む" to "飲む", "。" to null),
            pieces.map { piece -> piece.segments.plainText() to piece.word?.written },
        )
    }

    /**
     * 来 reads く in 来る, き in 来ます and こ in 来ない, so the dictionary
     * form's reading is wrong on every row but its own. Nothing in a
     * pair of spellings can say that — it takes the paradigm the entry's
     * pos code names, which is the whole reason those codes are carried
     * as far as the renderer.
     */
    @Test
    fun `an irregular verb takes the reading of the form the sentence writes`() {
        listOf(
            "来ない" to "[来[こ]]ない",
            "来ます" to "[来[き]]ます",
            "来た" to "[来[き]]た",
            "来て" to "[来[き]]て",
            "来なかった" to "[来[こ]]なかった",
            "来ました" to "[来[き]]ました",
            "来たら" to "[来[き]]たら",
        ).forEach { (surface, expected) ->
            assertEquals(
                expected,
                rendered(
                    sentence(surface, BreakdownWord("来る", "くる", surface = surface, entryId = KURU)),
                    entryPos = mapOf(KURU to listOf("vk")),
                ),
                surface,
            )
        }
    }

    @Test
    fun `an irregular stem the paradigm does not place takes no reading`() {
        // 来 on its own is not a row of anything, and 来 reads く, き and
        // こ across the table, so there is no reading to give it.
        assertEquals(
            "来",
            rendered(
                sentence("来", BreakdownWord("来る", "くる", surface = "来", entryId = KURU)),
                entryPos = mapOf(KURU to listOf("vk")),
            ),
        )
    }

    /**
     * The regular verbs the paradigm must not cost anything. 行った
     * rewrites its okurigana as thoroughly as 来ない does, and the
     * difference — that 行 reads い on every row — is exactly what the
     * table shows.
     */
    @Test
    fun `a regular verb keeps its stem's reading through an inflection`() {
        assertEquals(
            "[行[い]]った",
            rendered(
                sentence("行った", BreakdownWord("行く", "いく", surface = "行った", entryId = IKU)),
                entryPos = mapOf(IKU to listOf("v5k-s")),
            ),
        )
        // Not a row of the table at all, and it does not need to be: 食
        // reads た everywhere the paradigm writes it, so the spelling
        // agreement is allowed to carry it.
        assertEquals(
            "[食[た]]べさせて",
            rendered(
                sentence("食べさせて", BreakdownWord("食べる", "たべる", surface = "食べさせて", entryId = TABERU)),
                entryPos = mapOf(TABERU to listOf("v1")),
            ),
        )
    }

    /**
     * The codes are load-bearing, and this is what says so. Without
     * them the same sentence falls back to comparing spellings, which
     * for 来る produces the reading of its dictionary form — 来[く]ない,
     * where the word reads こない.
     *
     * Asserted rather than left implicit because it is the one path on
     * this tab that can still draw a wrong reading: the part-of-speech
     * query is allowed to fail, and the run that loses it also leaves
     * nothing tappable. Two irregular verbs are affected and no other
     * shape is; a change that made this test pass by refusing instead
     * would be an improvement, not a regression.
     */
    @Test
    fun `an irregular verb falls back on its dictionary form when no paradigm is known`() {
        assertEquals(
            "[来[く]]ない",
            rendered(sentence("来ない", BreakdownWord("来る", "くる", surface = "来ない", entryId = KURU))),
        )
    }

    @Test
    fun `the pieces always spell the stored sentence back exactly`() {
        // The one property that must never break: what is on screen is
        // what Tatoeba holds, character for character.
        listOf(
            sentence(
                "もっと果物を食べるべきです。",
                BreakdownWord("果物", "くだもの"),
                BreakdownWord("食べる", "たべる"),
                BreakdownWord("可き", "べき", surface = "べき"),
            ),
            sentence(
                "彼は２０歳です。",
                BreakdownWord("二十歳", "はたち", surface = "２０歳"),
            ),
            sentence("ふうん。"),
            sentence(
                "水を飲む。",
                BreakdownWord("犬", "いぬ"),
            ),
        ).forEach { example ->
            assertEquals(
                example.japanese,
                sentencePieces(example).flatMap { it.segments }.plainText(),
            )
        }
    }
}
