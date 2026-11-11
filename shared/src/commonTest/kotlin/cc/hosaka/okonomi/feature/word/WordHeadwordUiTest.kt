package cc.hosaka.okonomi.feature.word

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.lang.LocalLearningLanguage
import cc.hosaka.okonomi.ui.furigana.LocalFuriganaShown
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.test.entryDetail
import cc.hosaka.okonomi.ui.test.entrySense
import kotlin.test.Test
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_section_reading
import org.jetbrains.compose.resources.stringResource

/**
 * The Word tab's Reading section: it lists what the headword's own ruby
 * does not already show, which for the common case of a single reading
 * is nothing at all.
 *
 * Only the section is asserted here. Whether the ruby is drawn, and
 * which string it is, cannot be seen on this SDK — the ruby goes
 * through a platform `TextView` — and asserting the *absence* of a
 * reading here would be worse than nothing: it passes for the reason the
 * ruby is invisible, and would pass more easily if the ruby were
 * deleted. `RubyRenderingTest` holds that end on the SDK where it is
 * visible, and `EntryReadingsTest` holds which reading is chosen.
 */
@OptIn(ExperimentalTestApi::class)
class WordHeadwordUiTest : ComposeUiTestBase() {

    @Test
    fun `an entry with one reading shows no reading section`() = runComposeUiTest {
        lateinit var heading: String
        setContent {
            heading = stringResource(Res.string.entry_section_reading)
            WordTab(
                entry = entryDetail(
                    headword = "相殺",
                    forms = listOf(EntryForm("相殺", isCommon = true)),
                    readings = listOf(EntryReading("そうさい", emptyList(), isCommon = true)),
                ),
                contentPadding = PaddingValues(),
            )
        }

        onNodeWithText("相殺").assertIsDisplayed()
        onNodeWithText(heading).assertDoesNotExist()
    }

    @Test
    fun `a second reading is listed while the first stays over the headword`() = runComposeUiTest {
        lateinit var heading: String
        setContent {
            heading = stringResource(Res.string.entry_section_reading)
            WordTab(
                entry = entryDetail(
                    headword = "相殺",
                    forms = listOf(EntryForm("相殺", isCommon = true)),
                    readings = listOf(
                        EntryReading("そうさい", emptyList(), isCommon = true),
                        EntryReading("そうさつ", emptyList(), isCommon = false),
                    ),
                ),
                contentPadding = PaddingValues(),
            )
        }

        onNodeWithText(heading).assertIsDisplayed()
        // The second reading is listed; the first is the headword's ruby
        // and is not repeated as a row (EntryReadingsTest).
        onNodeWithText("そうさつ").assertIsDisplayed()
    }

    /**
     * The headword's line box is tall enough to hold the reading drawn
     * above it, which is the geometry the clipped dakuten came down to:
     * `displayMedium`'s own 52sp box leaves 3.5sp above 45sp characters
     * where the ruby reaches 21.6sp, so the rest was drawn above the
     * composable and lost to the top edge of this tab's `LazyColumn`.
     *
     * The requirement is stated here from the two scales the renderer
     * places the ruby with — ruby at 0.45 of the base, gap at 0.03,
     * the characters centred, so the box must be `1 + 2 * (0.45 + 0.03)`
     * times the font size — rather than read back from
     * `rubyReadyLineHeight`, so this cannot pass by agreeing with a
     * formula that has itself gone wrong. Dropping `withRoomForRuby`
     * from the headword leaves the box at 1.48 times the font size and
     * fails it.
     *
     * The clipped pixels themselves are not visible here; see
     * `RubyRoomTest`.
     */
    @Test
    fun `the headword's line box holds the reading drawn above it`() = runComposeUiTest {
        setContent {
            WordTab(
                entry = entryDetail(
                    headword = "葡萄棚",
                    forms = listOf(EntryForm("葡萄棚", isCommon = false)),
                    readings = listOf(EntryReading("ぶどうだな", emptyList(), isCommon = false)),
                ),
                contentPadding = PaddingValues(),
            )
        }

        val style = onNodeWithText("葡萄棚").textLayout().layoutInput.style
        val required = style.fontSize.value * (1f + 2f * (0.45f + 0.03f))
        assertTrue(
            style.lineHeight.value >= required - 0.001f,
            "the headword needs a ${required}sp line box for its ruby, got ${style.lineHeight}",
        )
    }

    /**
     * And a headword with no reading to draw does not pay for the room.
     * ありがとう aligns to segments carrying no ruby at all, so the
     * taller box would be 43.2sp of empty page above and below a word
     * that will never have anything over it.
     *
     * Asserted against the same requirement the case above uses, so the
     * pair states one rule from both sides rather than two numbers.
     */
    @Test
    fun `a headword with no reading takes the ordinary line box`() = runComposeUiTest {
        setContent {
            WordTab(
                entry = entryDetail(
                    headword = "ありがとう",
                    forms = emptyList(),
                    readings = listOf(EntryReading("ありがとう", emptyList(), isCommon = true)),
                ),
                contentPadding = PaddingValues(),
            )
        }

        val style = onNodeWithText("ありがとう").textLayout().layoutInput.style
        val roomForRuby = style.fontSize.value * (1f + 2f * (0.45f + 0.03f))
        assertTrue(
            style.lineHeight.value < roomForRuby,
            "nothing is drawn above these characters, got a ${style.lineHeight} box",
        )
    }

    /**
     * While furigana is hidden nothing is drawn above the headword, so it
     * takes the ordinary box: the room above would be a gap under a word
     * with nothing over it.
     */
    @Test
    fun `a headword with furigana hidden takes the ordinary line box`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalFuriganaShown provides false) {
                WordTab(
                    entry = entryDetail(
                        headword = "葡萄棚",
                        forms = listOf(EntryForm("葡萄棚", isCommon = false)),
                        readings = listOf(EntryReading("ぶどうだな", emptyList(), isCommon = false)),
                    ),
                    contentPadding = PaddingValues(),
                )
            }
        }

        val style = onNodeWithText("葡萄棚").textLayout().layoutInput.style
        val roomForRuby = style.fontSize.value * (1f + 2f * (0.45f + 0.03f))
        assertTrue(
            style.lineHeight.value < roomForRuby,
            "no reading is drawn above these characters, got a ${style.lineHeight} box",
        )
    }

    /**
     * For a reader learning English the gloss they tapped is the
     * headword, and the word is the line beneath it — even where the
     * gloss is not the entry's first.
     */
    @Test
    fun `learning english the tapped gloss is the headword with the word beneath`() = runComposeUiTest {
        setContent {
            LearningEnglish {
                WordTab(
                    entry = okonau(),
                    contentPadding = PaddingValues(),
                    titleGloss = "to do",
                )
            }
        }

        // Exact text, so the gloss line "to do" of the senses below — a
        // separate text after its bullet — is the only other match, and
        // it sits lower down still.
        val headword = onAllNodesWithText("to do", useUnmergedTree = true).fetchSemanticsNodes()
            .minBy { it.boundsInRoot.top }
        val word = onNodeWithText("行う", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(headword.boundsInRoot.top < word.boundsInRoot.top, "the gloss heads the tab, the word beneath it")
        val glossLine = onAllNodesWithText("to do", useUnmergedTree = true).fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.top }
        assertTrue(word.boundsInRoot.top < glossLine.boundsInRoot.top, "the headword is above the senses")
    }

    /** The entry view hands the state's gloss on to the tab it titles. */
    @Test
    fun `the entry screen titles its word tab by the state's gloss`() = runComposeUiTest {
        setContent {
            ScreenHost {
                LearningEnglish {
                    EntryScreen(
                        EntryState(
                            entryId = 1L,
                            content = EntryContentState.Ready(okonau()),
                            titleGloss = "to do",
                        ),
                    )
                }
            }
        }

        val headword = onAllNodesWithText("to do", useUnmergedTree = true).fetchSemanticsNodes()
            .minBy { it.boundsInRoot.top }
        val word = onNodeWithText("行う", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(headword.boundsInRoot.top < word.boundsInRoot.top, "the gloss heads the tab, the word beneath it")
    }

    /**
     * No Reading row stands in for the ruby that is gone (Alex,
     * 2026-11-11). Whether a ruby is drawn cannot be seen at this SDK;
     * `RubyRenderingTest` holds that end.
     */
    @Test
    fun `learning english no reading row is added for the hidden ruby`() = runComposeUiTest {
        lateinit var heading: String
        setContent {
            heading = stringResource(Res.string.entry_section_reading)
            LearningEnglish {
                WordTab(
                    entry = okonau(),
                    contentPadding = PaddingValues(),
                    titleGloss = "to do",
                )
            }
        }

        onNodeWithText("行う", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(heading).assertDoesNotExist()
        onNodeWithText("おこなう", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * And the readings the ruby never covered are not listed either.
     * 今日 reads きょう over the headword and こんにち in the Reading
     * section; with the ruby hidden, listing こんにち alone would show the
     * rarer reading and never the one the word is known by.
     */
    @Test
    fun `learning english the other readings are not listed`() = runComposeUiTest {
        lateinit var heading: String
        setContent {
            heading = stringResource(Res.string.entry_section_reading)
            LearningEnglish {
                WordTab(
                    entry = entryDetail(
                        headword = "今日",
                        forms = listOf(EntryForm("今日", isCommon = true)),
                        readings = listOf(
                            EntryReading("きょう", emptyList(), isCommon = true),
                            EntryReading("こんにち", emptyList(), isCommon = true),
                        ),
                        senses = listOf(entrySense(glosses = listOf("today"))),
                    ),
                    contentPadding = PaddingValues(),
                    titleGloss = "today",
                )
            }
        }

        onNodeWithText("今日", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("こんにち", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(heading).assertDoesNotExist()
    }

    /**
     * A word written in kana alone is its own reading. It gets no ruby
     * and no section repeating the headword underneath it.
     */
    @Test
    fun `a kana headword stands alone`() = runComposeUiTest {
        lateinit var heading: String
        setContent {
            heading = stringResource(Res.string.entry_section_reading)
            WordTab(
                entry = entryDetail(
                    headword = "ラーメン",
                    forms = emptyList(),
                    readings = listOf(EntryReading("ラーメン", emptyList(), isCommon = false)),
                ),
                contentPadding = PaddingValues(),
            )
        }

        onNodeWithText("ラーメン").assertIsDisplayed()
        onNodeWithText(heading).assertDoesNotExist()
    }
}

/** 行う: two glosses in its first sense, and one reading. */
private fun okonau() = entryDetail(
    headword = "行う",
    forms = listOf(EntryForm("行う", isCommon = true)),
    readings = listOf(EntryReading("おこなう", emptyList(), isCommon = true)),
    senses = listOf(entrySense(posCodes = listOf("v5u"), glosses = listOf("to perform", "to do"))),
)

/** [content] as the app draws it for a reader learning English. */
@Composable
private fun LearningEnglish(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLearningLanguage provides LearningLanguage.English,
        LocalFuriganaShown provides false,
        content = content,
    )
}

/**
 * The layout the node reports, which is where the resolved line height
 * can be read; the semantics tree itself carries only the string.
 */
private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
    assertTrue(action?.action?.invoke(results) == true, "the node reports no text layout")
    return results.first()
}
