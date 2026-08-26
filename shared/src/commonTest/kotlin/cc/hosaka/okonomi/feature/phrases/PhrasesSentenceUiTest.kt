package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the tab actually draws for one example.
 *
 * The ruby itself is inline content and invisible to the semantics tree
 * (see `SearchFuriganaUiTest`), so what is asserted here is the text
 * layer: that the sentence reaches the screen as its own characters,
 * cut into the pieces a tap can address, with the English under it and
 * no third line. [SentenceFuriganaTest] holds the readings.
 */
@OptIn(ExperimentalTestApi::class)
class PhrasesSentenceUiTest : ComposeUiTestBase() {

    @Test
    fun `the sentence reaches the screen as the corpus writes it`() = runComposeUiTest {
        setContent { PhrasesUnderTest() }
        waitForIdle()

        // 学校で話をします。 in order: every piece of it, and nothing
        // between them. A line rebuilt from the breakdown would show
        // 為る here instead of します.
        listOf("学校", "で", "話", "を", "します", "。").forEach { piece ->
            onNodeWithText(piece).assertIsDisplayed()
        }
    }

    /**
     * Presence is not order. Every piece of the sentence is its own text
     * node now, and a list drawn back to front — 。します を 話 で 学校 —
     * satisfies every assertion above while being no sentence at all.
     * So the strings are read off the tree in the order the tree holds
     * them and matched as one unbroken run: that is the "in order, and
     * nothing between them" the paragraph above claims.
     */
    @Test
    fun `the pieces are drawn in the order the sentence writes them`() = runComposeUiTest {
        setContent { PhrasesUnderTest() }
        waitForIdle()

        val drawn = drawnTexts()
        val expected = listOf("学校", "で", "話", "を", "します", "。")
        val start = drawn.indexOf(expected.first())

        assertTrue(start >= 0, "the sentence is not on screen at all: $drawn")
        assertEquals(
            expected,
            drawn.subList(start, minOf(start + expected.size, drawn.size)),
            "the sentence must be drawn as one run in its own order: $drawn",
        )
    }

    @Test
    fun `the English translation sits under the sentence`() = runComposeUiTest {
        setContent { PhrasesUnderTest() }
        waitForIdle()

        onNodeWithText("I have a talk at school.").assertIsDisplayed()
    }

    /**
     * The affordance. A content word is drawn in the dynamic primary
     * colour and an inert one is not — the rule the deleted breakdown
     * row had, which this tab shipped without and Alex asked back after
     * reading it on a device.
     *
     * Read off the text layout rather than off a span: the colour is set
     * on the line's style, not on a range inside it, so `spanStyles` —
     * where a *match* highlight would show up — is empty either way.
     */
    @Test
    fun `a word that can be tapped is drawn in the primary colour`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            PhrasesUnderTest(tappable = setOf(GAKKOU, HANASHI))
        }
        waitForIdle()

        assertEquals(primary, onNodeWithText("学校").styleColor())
        assertEquals(primary, onNodeWithText("話").styleColor())
    }

    @Test
    fun `a word that cannot be tapped keeps the colour of the sentence`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            PhrasesUnderTest(tappable = setOf(GAKKOU, HANASHI))
        }
        waitForIdle()

        // The particle and the full stop: neither is a word to look up,
        // and colouring them would say they were.
        assertTrue(onNodeWithText("を").styleColor() != primary, "を must not be coloured")
        assertTrue(onNodeWithText("。").styleColor() != primary, "punctuation must not be coloured")
    }

    /**
     * The breakdown row is gone, and this is what says so: it drew every
     * word in its dictionary form, so 為る on screen means the row came
     * back. With the sentence still above it, both would render and
     * every other assertion here would pass.
     */
    @Test
    fun `no second copy of the sentence is drawn in dictionary forms`() = runComposeUiTest {
        setContent { PhrasesUnderTest() }
        waitForIdle()

        onNodeWithText("為る").assertDoesNotExist()
        onNodeWithText("学校 (がっこう)").assertDoesNotExist()
    }
}

/**
 * The colour the node's text is laid out in.
 *
 * Through the layout result, because that is where a colour set on the
 * style ends up — the semantics tree carries the string and its span
 * styles, and a line coloured whole has no spans at all.
 */
private fun SemanticsNodeInteraction.styleColor(): Color {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
    assertTrue(action?.action?.invoke(results) == true, "the node reports no text layout")
    return results.first().layoutInput.style.color
}

/**
 * Every string the line itself draws, in the order the semantics tree
 * holds them, which for a `FlowRow` of text nodes is the order they are
 * laid out in.
 *
 * A ruby unit is not descended into. It clears its own semantics
 * precisely because everything inside it is a second copy — the word
 * again, and a reading no screen reader should announce as a word of
 * its own — and counting that copy would report 学校 twice and break
 * the run this asserts. `RubyRenderingTest` walks into exactly those
 * boxes, on purpose, for the opposite reason.
 */
private fun SemanticsNodeInteractionsProvider.drawnTexts(): List<String> =
    onRoot(useUnmergedTree = true).fetchSemanticsNode().texts()

private fun SemanticsNode.texts(): List<String> = buildList {
    if (config.isClearingSemantics) return@buildList
    config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text?.let { add(it) }
    children.forEach { addAll(it.texts()) }
}

private val GAKKOU = BreakdownWord("学校", "がっこう")

private val HANASHI = BreakdownWord("話", "はなし")

@Composable
private fun PhrasesUnderTest(tappable: Set<BreakdownWord> = emptySet()) {
    ScreenHost {
        PhrasesTabContent(
            state = PhrasesTabState(
                content = PhrasesTabContentState.Ready(
                    sentences = listOf(
                        ExampleSentence(
                            id = 1L,
                            japanese = "学校で話をします。",
                            english = "I have a talk at school.",
                            words = listOf(
                                GAKKOU,
                                BreakdownWord("で", null),
                                HANASHI,
                                BreakdownWord("を", null),
                                BreakdownWord("為る", "する", surface = "します"),
                            ),
                        ),
                    ),
                    tappableWords = tappable,
                ),
            ),
            contentPadding = PaddingValues(),
        )
    }
}
