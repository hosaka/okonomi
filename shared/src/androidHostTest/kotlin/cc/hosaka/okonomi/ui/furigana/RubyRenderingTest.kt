package cc.hosaka.okonomi.ui.furigana

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.feature.forms.FormsTab
import cc.hosaka.okonomi.feature.phrases.PhrasesTabContent
import cc.hosaka.okonomi.feature.phrases.PhrasesTabContentState
import cc.hosaka.okonomi.feature.phrases.PhrasesTabState
import cc.hosaka.okonomi.feature.word.WordTab
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.test.entryDetail
import cc.hosaka.okonomi.ui.test.entrySense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * That a reading is drawn at all, and that it is drawn *over* the word
 * rather than in place of it.
 *
 * Pinned here and nowhere else, for a structural reason.
 * `TextSpacingRemoved` routes API 28 and up through a platform
 * `TextView` inside an `AndroidView`, which never reaches the semantics
 * tree — so at the suite's usual `@Config(sdk = [36])` a ruby is
 * invisible to every assertion, and deleting one leaves the suite green.
 * Three headline behaviours were in exactly that state: dropping the
 * Forms tab's reading, dropping the Word headword's, and swapping base
 * and ruby all passed the whole suite. This class runs the same
 * composables on the pre-API-28 branch, where both halves are
 * `BasicText` and both are therefore in the tree.
 *
 * Base and ruby are told apart by where they sit in it rather than by
 * how they look: Robolectric lays every glyph out to no width at all, so
 * font sizes and bounds say nothing here, while the shape of the unit —
 * the word drawn directly in it, the reading inside the box that lifts
 * it above the line — is exactly the arrangement being asserted.
 *
 * The distinction is worth stating exactly, because it is what the
 * suite kept losing. Swapping a unit's base and its ruby DOES fail
 * three assertions at the ordinary SDK, since the tree carries the base
 * characters and would carry the wrong ones. Deleting the ruby
 * ALTOGETHER fails nothing there, since what is left is the same base
 * characters — `appendInlineContent(id, segment.text)` puts them in the
 * tree whether or not anything is drawn above them. So: a wrong reading
 * is caught anywhere; a missing reading is caught only here. Every site
 * that draws ruby needs a case in this file.
 *
 * What stays unassertable, on any SDK, is where that box is *put*: the
 * offset comes from a `graphicsLayer` translation over font metrics, and
 * neither it nor the vertical rhythm it produces reaches semantics.
 * Those want a device and Alex's eyes. Which string is the base does
 * not.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [27])
@OptIn(ExperimentalTestApi::class)
class RubyRenderingTest {

    @Test
    fun `a segment draws its reading over its text rather than instead of it`() = runComposeUiTest {
        setContent {
            ScreenHost {
                FuriganaText(
                    segments = listOf(
                        FuriganaSegment("食", "た"),
                        FuriganaSegment("べる"),
                    ),
                )
            }
        }

        // Swap the two and this reads た to 食.
        assertEquals(listOf("食" to "た"), rubyUnits())
    }

    /**
     * Alex's report, at the other end from `TitleFuriganaTest`: a match
     * on part of an undivided run must reach the ruby and stop there.
     * The reading is drawn in two pieces so the matched kana can be
     * styled without the rest, and the word stays in one — untouched.
     */
    @Test
    fun `a match on part of a reading splits only the ruby`() = runComposeUiTest {
        setContent {
            ScreenHost {
                FuriganaText(
                    segments = listOf(
                        FuriganaSegment(
                            text = "相殺関税",
                            reading = "そうさいかんぜい",
                            highlight = FuriganaSegment.Highlight.PartOfReading(0..3),
                        ),
                    ),
                )
            }
        }

        val unit = rubyUnitShapes().single()
        assertEquals(listOf("相殺関税"), unit.word, "the word is drawn whole and plain")
        assertEquals(listOf("そうさい", "かんぜい"), unit.reading, "only そうさい can take the highlight")
    }

    @Test
    fun `a match covering a whole unit leaves both halves undivided`() = runComposeUiTest {
        setContent {
            ScreenHost {
                FuriganaText(
                    segments = listOf(
                        FuriganaSegment("食", "た", FuriganaSegment.Highlight.Whole),
                        FuriganaSegment("べる"),
                    ),
                )
            }
        }

        val unit = rubyUnitShapes().single()
        assertEquals(listOf("食"), unit.word)
        assertEquals(listOf("た"), unit.reading, "a whole match needs no cut, and draws as it always did")
    }

    @Test
    fun `a segment with no reading draws no ruby`() = runComposeUiTest {
        setContent {
            ScreenHost {
                FuriganaText(segments = listOf(FuriganaSegment("食べる")))
            }
        }

        assertEquals(emptyList(), rubyUnits())
        assertTrue(drawnTexts().contains("食べる"), "the word is still drawn: ${drawnTexts()}")
    }

    @Test
    fun `the word headword carries its reading`() = runComposeUiTest {
        setContent {
            ScreenHost {
                WordTab(
                    entry = entryDetail(
                        headword = "相殺",
                        forms = listOf(EntryForm("相殺", isCommon = true)),
                        readings = listOf(EntryReading("そうさい", emptyList(), isCommon = true)),
                    ),
                    contentPadding = PaddingValues(),
                )
            }
        }

        assertEquals(listOf("相殺" to "そうさい"), rubyUnits())
    }

    /**
     * A reading JMdict marks `re_nokanji` belongs to no written form, so
     * it is not the one set over the headword — 刻々 reads こくこく, and
     * ギザギザ is a reading of the word without being a reading of those
     * characters. It stays listed; it is simply never the ruby.
     */
    @Test
    fun `a headword takes no reading the entry does not claim for it`() = runComposeUiTest {
        setContent {
            ScreenHost {
                WordTab(
                    entry = entryDetail(
                        headword = "刻々",
                        forms = listOf(EntryForm("刻々", isCommon = true)),
                        readings = listOf(
                            EntryReading("ギザギザ", emptyList(), isCommon = false, noKanji = true),
                            EntryReading("こくこく", emptyList(), isCommon = true),
                        ),
                    ),
                    contentPadding = PaddingValues(),
                )
            }
        }

        assertEquals(listOf("刻々" to "こくこく"), rubyUnits())
        assertTrue(drawnTexts().contains("ギザギザ"), "the reading is still listed: ${drawnTexts()}")
    }

    /**
     * 為 reads す, し and さ across the table, which is the whole reason
     * the Forms tab was given furigana at all. Cutting the reading out
     * of `FormsTab` leaves every other Forms assertion green.
     */
    @Test
    fun `the forms table carries the readings of a stem that shifts`() = runComposeUiTest {
        setContent {
            ScreenHost {
                FormsTab(
                    entry = entryDetail(
                        headword = "為る",
                        forms = listOf(EntryForm("為る", isCommon = true)),
                        readings = listOf(EntryReading("する", emptyList(), isCommon = true)),
                        senses = listOf(entrySense(posCodes = listOf("vs-i"), glosses = listOf("to do"))),
                    ),
                    contentPadding = PaddingValues(),
                )
            }
        }

        val units = rubyUnits()
        assertTrue(units.isNotEmpty(), "the table sets readings over its shifting stem")
        assertTrue(units.all { (base, _) -> base == "為" }, "only 為 shifts: $units")
        assertEquals(setOf("す", "し", "さ"), units.map { (_, reading) -> reading }.toSet())
        // 出来る sits in the same table and reads でき in both its rows,
        // so it is left plain while 為 around it is not.
        assertTrue(drawnTexts().contains("出来る"), "the potential is drawn whole: ${drawnTexts()}")
    }

    /**
     * The Phrases tab is the third site, and it was in exactly the
     * state the two above were rescued from: collapsing every piece of
     * the sentence to one plain segment — every reading on the tab
     * gone — left the whole suite green, because the base characters
     * reach the tree either way and they are all the tree can see.
     */
    @Test
    fun `a sentence carries the readings of its words`() = runComposeUiTest {
        setContent {
            PhrasesUnderTest(
                japanese = "学校で勉強する。",
                words = listOf(
                    BreakdownWord("学校", "がっこう"),
                    BreakdownWord("で", null),
                    BreakdownWord("勉強", "べんきょう"),
                ),
            )
        }

        assertEquals(listOf("学校" to "がっこう", "勉強" to "べんきょう"), rubyUnits())
    }

    /**
     * `transferReading`'s output, across the render boundary: the stem
     * takes the ruby and the inflected tail is drawn plain beside it,
     * on the line rather than above it.
     */
    @Test
    fun `an inflected word sets its reading over the stem alone`() = runComposeUiTest {
        setContent {
            PhrasesUnderTest(
                japanese = "父は食べない。",
                words = listOf(
                    BreakdownWord("父", "ちち"),
                    BreakdownWord("は", null),
                    BreakdownWord("食べる", "たべる", surface = "食べない"),
                ),
            )
        }

        assertEquals(listOf("父" to "ちち", "食" to "た"), rubyUnits())
        // The piece's own line carries all four characters — the ruby
        // box holds a second copy of 食 alone — so べない is on the line
        // rather than above it.
        assertTrue(drawnTexts().contains("食べない"), "the surface is drawn whole: ${drawnTexts()}")
    }

    /** A refused transfer draws the surface and no ruby at all. */
    @Test
    fun `a word whose reading will not transfer draws no ruby`() = runComposeUiTest {
        setContent {
            PhrasesUnderTest(
                japanese = "彼は２０歳です。",
                words = listOf(
                    BreakdownWord("彼", "かれ"),
                    BreakdownWord("は", null),
                    BreakdownWord("二十歳", "はたち", surface = "２０歳"),
                ),
            )
        }

        assertEquals(listOf("彼" to "かれ"), rubyUnits(), "はたち belongs to 二十歳, not to ２０歳")
        assertTrue(drawnTexts().contains("２０歳"), "the characters are still drawn: ${drawnTexts()}")
    }

    /**
     * The conjugated path: 来る reads くる and 来ない reads こない, so the
     * ruby over 来 here must be こ. Carrying the dictionary form's own
     * reading — which is what a spelling comparison does — puts く there
     * and teaches a reading that does not exist.
     */
    @Test
    fun `an irregular verb draws the reading of the form on screen`() = runComposeUiTest {
        setContent {
            PhrasesUnderTest(
                japanese = "彼は来ない。",
                words = listOf(
                    BreakdownWord("彼", "かれ"),
                    BreakdownWord("は", null),
                    BreakdownWord("来る", "くる", surface = "来ない", entryId = 1L),
                ),
                entryPos = mapOf(1L to listOf("vk")),
            )
        }

        assertEquals(listOf("彼" to "かれ", "来" to "こ"), rubyUnits())
    }

    @Test
    fun `a table whose stem never shifts carries no ruby`() = runComposeUiTest {
        setContent {
            ScreenHost {
                FormsTab(
                    entry = entryDetail(
                        headword = "食べる",
                        forms = listOf(EntryForm("食べる", isCommon = true)),
                        readings = listOf(EntryReading("たべる", emptyList(), isCommon = true)),
                        senses = listOf(entrySense(posCodes = listOf("v1"), glosses = listOf("to eat"))),
                    ),
                    contentPadding = PaddingValues(),
                )
            }
        }

        assertEquals(emptyList(), rubyUnits(), "食 reads た on every row and needs no ruby saying so")
        assertTrue(drawnTexts().contains("食べる"), "the table still draws its forms")
    }
}

/** One example sentence through the real tab, with nothing else on it. */
@Composable
private fun PhrasesUnderTest(
    japanese: String,
    words: List<BreakdownWord>,
    entryPos: Map<Long, List<String>> = emptyMap(),
) {
    ScreenHost {
        PhrasesTabContent(
            state = PhrasesTabState(
                content = PhrasesTabContentState.Ready(
                    sentences = listOf(
                        ExampleSentence(id = 1L, japanese = japanese, english = "translation", words = words),
                    ),
                    entryPos = entryPos,
                ),
            ),
            contentPadding = PaddingValues(),
        )
    }
}

/**
 * One kanji-with-ruby box on screen: the word drawn on the line, and
 * the reading floated above it — each as the pieces it was drawn in,
 * because a highlight covering part of a half cuts that half in two.
 */
private class RubyUnitShape(val word: List<String>, val reading: List<String>) {
    val pair: Pair<String, String> get() = word.joinToString("") to reading.joinToString("")
}

/**
 * Every ruby unit on screen, as the word paired with its reading.
 *
 * The unit is found by the semantics it clears — [RubyUnit] clears its
 * own, so the ruby is never announced as a word of its own — and under
 * it the word is the first child and the reading the second. That
 * arrangement is what is being asserted: the word is what the line
 * reads, the reading is what floats above it.
 */
private fun SemanticsNodeInteractionsProvider.rubyUnits(): List<Pair<String, String>> =
    rubyUnitShapes().map { it.pair }

private fun SemanticsNodeInteractionsProvider.rubyUnitShapes(): List<RubyUnitShape> =
    onRoot(useUnmergedTree = true).fetchSemanticsNode().rubyUnitShapes()

private fun SemanticsNode.rubyUnitShapes(): List<RubyUnitShape> = buildList {
    val cleared = config.isClearingSemantics
    if (cleared && children.size == 2) {
        add(RubyUnitShape(word = children[0].texts(), reading = children[1].texts()))
    }
    children.forEach { addAll(it.rubyUnitShapes()) }
}

/** Every string drawn anywhere on screen. */
private fun SemanticsNodeInteractionsProvider.drawnTexts(): List<String> =
    onRoot(useUnmergedTree = true).fetchSemanticsNode().texts()

private fun SemanticsNode.texts(): List<String> = buildList {
    ownText?.let { add(it) }
    children.forEach { addAll(it.texts()) }
}

private val SemanticsNode.ownText: String?
    get() = config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text
