package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.appearance_back
import okonomi.shared.generated.resources.appearance_show_hints
import org.jetbrains.compose.resources.stringResource

/**
 * The hints switch is one node: the label, the on/off state and the
 * switch role together, and a tap anywhere on it reports the new value.
 */
@OptIn(ExperimentalTestApi::class)
class AppearanceScreenUiTest : ComposeUiTestBase() {
    @Test
    fun `the whole row is one switch carrying its label`() = runComposeUiTest {
        var label = ""
        val changes = mutableListOf<Boolean>()
        setContent {
            label = stringResource(Res.string.appearance_show_hints)
            AppearanceUnderTest(AppearanceState(showHints = true, onShowHintsChange = { changes += it }))
        }

        val row = onNode(
            hasText(label) and isToggleable() and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        )
        onAllNodes(isToggleable()).fetchSemanticsNodes().let { assertEquals(1, it.size) }
        row.assertIsOn()
        row.performClick()

        assertEquals(listOf(false), changes)
    }

    @Test
    fun `the row shows a stored off`() = runComposeUiTest {
        var label = ""
        setContent {
            label = stringResource(Res.string.appearance_show_hints)
            AppearanceUnderTest(AppearanceState(showHints = false, onShowHintsChange = {}))
        }

        onNode(hasText(label) and isToggleable()).assertIsOff()
    }

    /** Drawn on a guess, the switch would flip in front of the reader once the store answered. */
    @Test
    fun `no switch is drawn while the setting is unknown`() = runComposeUiTest {
        setContent {
            AppearanceUnderTest(AppearanceState(showHints = null, onShowHintsChange = {}))
        }

        onAllNodes(isToggleable()).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun `the row is disabled without a callback`() = runComposeUiTest {
        var label = ""
        setContent {
            label = stringResource(Res.string.appearance_show_hints)
            AppearanceUnderTest(AppearanceState(showHints = true, onShowHintsChange = null))
        }

        onNode(hasText(label) and isToggleable()).assertIsNotEnabled()
    }

    @Test
    fun `the back button pops the screen`() = runComposeUiTest {
        var back = ""
        val navigation = RecordingNavigationController()
        setContent {
            back = stringResource(Res.string.appearance_back)
            AppearanceUnderTest(AppearanceState(showHints = true, onShowHintsChange = {}), navigation)
        }

        onNodeWithContentDescription(back).performClick()

        assertEquals(1, navigation.pops)
    }
}

@Composable
private fun AppearanceUnderTest(
    state: AppearanceState,
    navigation: RecordingNavigationController = RecordingNavigationController(),
) {
    CompositionLocalProvider(LocalNavigationController provides navigation) {
        AppearanceScreen(state)
    }
}
