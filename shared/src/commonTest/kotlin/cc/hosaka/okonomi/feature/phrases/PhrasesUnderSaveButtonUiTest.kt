package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.feature.word.SaveEntryButton
import cc.hosaka.okonomi.feature.word.SaveEntryButtonDefaults
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import kotlin.test.Test
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_save
import org.jetbrains.compose.resources.stringResource

private const val COUNT = 30

/**
 * The English line of the last row, not the Japanese one: the Japanese is
 * drawn with furigana, which goes through an AndroidView that never
 * reaches the semantics tree. The English sits at the bottom of the same
 * row, so it is the lower of the two and the stricter thing to check.
 */
private val LAST_SENTENCE = "Example sentence $COUNT."

/**
 * The matrix row: with the save button present, the last sentence of the
 * Phrases tab can still be read.
 *
 * The list and the button are placed the way `EntryScreen` places them —
 * the same box, the same alignment, the same
 * [SaveEntryButtonDefaults.contentBottomPadding] — because the thing
 * under test is that arithmetic, and the tab itself cannot be reached
 * through `EntryScreen` in a host test (it queries the dictionary, which
 * has no provisioned file here).
 *
 * The height assertion is not decoration. Robolectric lays glyphs out at
 * zero width, and a list whose rows measured to nothing would put its
 * last row somewhere near the top of the screen and pass this test
 * without the padding doing any work at all.
 */
@OptIn(ExperimentalTestApi::class)
class PhrasesUnderSaveButtonUiTest : ComposeUiTestBase() {

    @Test
    fun `the last sentence sits above the save button rather than under it`() = runComposeUiTest {
        lateinit var save: String
        setContent {
            save = stringResource(Res.string.entry_save)
            PhrasesWithSaveButton()
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(COUNT - 1)
        waitForIdle()

        val lastSentence = onNodeWithText(LAST_SENTENCE).getBoundsInRoot()
        val button = onNodeWithContentDescription(save).getBoundsInRoot()

        assertTrue(
            lastSentence.height > 0.dp,
            "a row that measured to nothing would pass this test for the wrong reason",
        )
        assertTrue(
            lastSentence.bottom <= button.top,
            "the last sentence ends at ${lastSentence.bottom} and the button starts at ${button.top}",
        )
    }
}

@Composable
private fun PhrasesWithSaveButton() {
    ScreenHost {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            PhrasesTabContent(
                state = PhrasesTabState(
                    content = PhrasesTabContentState.Ready(
                        sentences = sentences(COUNT),
                        onShowMore = null,
                    ),
                ),
                contentPadding = PaddingValues(bottom = SaveEntryButtonDefaults.contentBottomPadding),
            )
            SaveEntryButton(
                isFavourite = false,
                onToggle = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd),
            )
        }
    }
}

private fun sentences(count: Int): List<ExampleSentence> = (1..count).map { index ->
    ExampleSentence(
        id = index.toLong(),
        japanese = "例文$index です。",
        english = "Example sentence $index.",
        words = listOf(
            BreakdownWord(text = "例文", reading = "れいぶん"),
            BreakdownWord(text = "です", reading = null),
        ),
    )
}
