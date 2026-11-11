package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.feature.navigation.navigationSavedStateConfiguration
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.lang.LocalLearningLanguage
import cc.hosaka.okonomi.ui.furigana.LocalFuriganaShown
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.test.entryDetail
import cc.hosaka.okonomi.ui.test.entrySense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.PolymorphicSerializer

/**
 * The route's gloss on its way to the screen: through saved state, and
 * through the two calls between [EntryRoute.Content] and the producer,
 * each of which defaults the gloss to null and so could drop it with
 * nothing failing to compile.
 *
 * A [ComposeUiTestBase] for the runtime as much as for the composition:
 * saved state is a `Bundle` on Android, which only Robolectric provides
 * on the host.
 */
@OptIn(ExperimentalTestApi::class)
class EntryRouteTest : ComposeUiTestBase() {

    /**
     * Through the serializer and configuration `rememberNavBackStack`
     * saves a section's stack with, so a rotation or a process death
     * brings the entry back titled the way the reader opened it. Marked
     * transient, like the link flag beside it, the gloss would come back
     * null and the entry would fall to its first gloss.
     */
    @Test
    fun `the tapped gloss survives saved state`() {
        val serializer = NavBackStackSerializer(PolymorphicSerializer(NavKey::class))
        val stack = NavBackStack<NavKey>(SearchRoute(), EntryRoute(1L, titleGloss = "to do"))

        val saved = encodeToSavedState(serializer, stack, navigationSavedStateConfiguration)
        val restored = decodeFromSavedState(serializer, saved, navigationSavedStateConfiguration)

        assertEquals(listOf(SearchRoute(), EntryRoute(1L, titleGloss = "to do")), restored.toList())
    }

    /**
     * The whole hop, from the route to a loaded Word tab: "to do" heads
     * it, not the "to perform" the entry lists first. Dropped at either
     * call, the gloss is null by the time the producer has it and the
     * entry falls back to its first.
     */
    @Test
    fun `a route opens its word tab under the gloss it carries`() = runComposeUiTest {
        setContent {
            LoadingFrom(load = { okonau() }) {
                EntryRoute(OKONAU_ID, titleGloss = "to do").Content()
            }
        }
        waitForIdle()

        onNodeWithText("to perform", useUnmergedTree = true).assertExists()
        val headword = onAllNodesWithText("to do", useUnmergedTree = true).fetchSemanticsNodes()
            .minBy { it.boundsInRoot.top }
        val word = onNodeWithText("行う", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(headword.boundsInRoot.top < word.boundsInRoot.top, "the tapped gloss heads the tab")
    }

    /**
     * The screen's state carries the gloss from its very first value —
     * the one handed out before the producer has run — through to the
     * loaded entry.
     */
    @Test
    fun `the screen state carries the route's gloss before and after loading`() = runComposeUiTest {
        val loaded = CompletableDeferred<Unit>()
        val states = mutableListOf<EntryState>()
        setContent {
            LoadingFrom(
                load = {
                    loaded.await()
                    okonau()
                },
            ) {
                val state = produceEntryScreenState(OKONAU_ID, titleGloss = "to do").value
                SideEffect { states += state }
            }
        }
        waitForIdle()

        assertIs<EntryContentState.Loading>(states.first().content)
        assertEquals("to do", states.first().titleGloss, "the first state already carries the gloss")

        loaded.complete(Unit)
        waitUntil { states.last().content is EntryContentState.Ready }

        assertEquals("to do", states.last().titleGloss)
    }
}

private const val OKONAU_ID = 1_000_000L

/** 行う: "to perform" first, "to do" second. */
private fun okonau(): EntryDetail = entryDetail(
    entryId = OKONAU_ID,
    headword = "行う",
    forms = listOf(EntryForm("行う", isCommon = true)),
    readings = listOf(EntryReading("おこなう", emptyList(), isCommon = true)),
    senses = listOf(entrySense(posCodes = listOf("v5u"), glosses = listOf("to perform", "to do"))),
)

/**
 * [content] for a reader learning English, with entries loaded by [load]
 * rather than from a dictionary file a host test does not have.
 */
@Composable
private fun LoadingFrom(
    load: suspend (Long) -> EntryDetail?,
    content: @Composable () -> Unit,
) {
    ScreenHost {
        CompositionLocalProvider(
            LocalEntryDetailLoader provides load,
            LocalLearningLanguage provides LearningLanguage.English,
            LocalFuriganaShown provides false,
            content = content,
        )
    }
}
