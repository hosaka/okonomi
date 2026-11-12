package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.lang.AppLanguage
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.appearance_back
import okonomi.shared.generated.resources.appearance_language
import okonomi.shared.generated.resources.appearance_language_system
import okonomi.shared.generated.resources.appearance_per_kanji_furigana
import okonomi.shared.generated.resources.appearance_per_kanji_furigana_hidden
import okonomi.shared.generated.resources.appearance_show_hints
import org.jetbrains.compose.resources.stringResource

/**
 * Each switch is one node: the label, the on/off state and the switch
 * role together, and a tap anywhere on it reports the new value.
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
    fun `the furigana switch is a row of its own reporting its own changes`() = runComposeUiTest {
        var label = ""
        val hints = mutableListOf<Boolean>()
        val furigana = mutableListOf<Boolean>()
        setContent {
            label = stringResource(Res.string.appearance_per_kanji_furigana)
            AppearanceUnderTest(
                AppearanceState(
                    showHints = true,
                    onShowHintsChange = { hints += it },
                    perKanjiFurigana = false,
                    onPerKanjiFuriganaChange = { furigana += it },
                ),
            )
        }

        val row = onNode(
            hasText(label) and isToggleable() and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        )
        row.assertIsOff()
        row.performClick()

        assertEquals(listOf(true), furigana)
        assertEquals(emptyList(), hints)
    }

    @Test
    fun `the furigana switch is disabled without a callback`() = runComposeUiTest {
        var label = ""
        setContent {
            label = stringResource(Res.string.appearance_per_kanji_furigana)
            AppearanceUnderTest(
                AppearanceState(
                    showHints = true,
                    onShowHintsChange = {},
                    perKanjiFurigana = true,
                    onPerKanjiFuriganaChange = null,
                ),
            )
        }

        onNode(hasText(label) and isToggleable()).assertIsNotEnabled()
    }

    /**
     * Learning English, the switch is shown off and cannot be moved, and
     * the row says why rather than leaving a dead switch unexplained. The
     * note is part of the switch's own node, so it is read out with it.
     */
    @Test
    fun `with furigana hidden the per-kanji switch is off and disabled and says why`() = runComposeUiTest {
        var label = ""
        var note = ""
        setContent {
            label = stringResource(Res.string.appearance_per_kanji_furigana)
            note = stringResource(Res.string.appearance_per_kanji_furigana_hidden)
            AppearanceUnderTest(
                AppearanceState(
                    showHints = true,
                    onShowHintsChange = {},
                    perKanjiFurigana = false,
                    onPerKanjiFuriganaChange = null,
                    furiganaHidden = true,
                ),
            )
        }

        val row = onNode(hasText(label) and hasText(note) and isToggleable())
        row.assertIsOff()
        row.assertIsNotEnabled()
    }

    @Test
    fun `with furigana shown the per-kanji switch carries no note`() = runComposeUiTest {
        var note = ""
        setContent {
            note = stringResource(Res.string.appearance_per_kanji_furigana_hidden)
            AppearanceUnderTest(
                AppearanceState(
                    showHints = true,
                    onShowHintsChange = {},
                    perKanjiFurigana = true,
                    onPerKanjiFuriganaChange = {},
                ),
            )
        }

        onNode(hasText(note)).assertDoesNotExist()
    }

    @Test
    fun `each switch shows its own value`() = runComposeUiTest {
        var hintsLabel = ""
        var furiganaLabel = ""
        setContent {
            hintsLabel = stringResource(Res.string.appearance_show_hints)
            furiganaLabel = stringResource(Res.string.appearance_per_kanji_furigana)
            AppearanceUnderTest(
                AppearanceState(
                    showHints = true,
                    onShowHintsChange = {},
                    perKanjiFurigana = false,
                    onPerKanjiFuriganaChange = {},
                ),
            )
        }

        onNode(hasText(hintsLabel) and isToggleable()).assertIsOn()
        onNode(hasText(furiganaLabel) and isToggleable()).assertIsOff()
    }

    /**
     * The row names the current choice and is one dropdown; the menu it
     * opens is a single choice of every option, each by name, the current
     * one selected, and a pick is reported. English and 日本語 are
     * written out because they read the same in every UI language by
     * design.
     */
    @Test
    fun `the language row opens every choice by name and reports a pick`() = runComposeUiTest {
        var label = ""
        var systemDefault = ""
        val picks = mutableListOf<AppLanguage>()
        setContent {
            label = stringResource(Res.string.appearance_language)
            systemDefault = stringResource(Res.string.appearance_language_system)
            AppearanceUnderTest(
                AppearanceState(appLanguage = AppLanguage.System, onAppLanguageChange = { picks += it }),
            )
        }

        onNode(hasText(label) and hasText(systemDefault) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .performClick()
        waitForIdle()

        onAllNodes(isLanguageOption).assertCountEquals(3)
        onNode(isLanguageOption and hasText(systemDefault) and isSelected()).assertExists()
        onNode(isLanguageOption and hasText("English") and isNotSelected()).assertExists()
        onNode(isLanguageOption and hasText("日本語") and isNotSelected()).performClick()
        waitForIdle()

        assertEquals(listOf(AppLanguage.Japanese), picks)
    }

    /** A pick closes the menu, as any single choice in a menu does. */
    @Test
    fun `the language menu closes after a pick`() = runComposeUiTest {
        var label = ""
        setContent {
            label = stringResource(Res.string.appearance_language)
            AppearanceUnderTest(AppearanceState(appLanguage = AppLanguage.System, onAppLanguageChange = {}))
        }
        onNode(hasText(label) and hasClickAction()).performClick()
        waitForIdle()
        onAllNodes(isLanguageOption).assertCountEquals(3)

        onNode(isLanguageOption and hasText("English")).performClick()
        waitForIdle()

        onAllNodes(isLanguageOption).assertCountEquals(0)
    }

    /**
     * Whether the menu is open is part of the row, through the expand and
     * collapse actions a screen reader reads that state from: expand
     * while it is closed, collapse while it is open.
     */
    @Test
    fun `the language row says whether its menu is open`() = runComposeUiTest {
        var label = ""
        setContent {
            label = stringResource(Res.string.appearance_language)
            AppearanceUnderTest(AppearanceState(appLanguage = AppLanguage.System, onAppLanguageChange = {}))
        }
        val row = onNode(hasText(label) and hasClickAction())
        row.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Collapse))

        row.performClick()
        waitForIdle()

        row.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))
    }

    /** The producer hands the choice and its callback over together; half of the pair draws nothing. */
    @Test
    fun `no language row is drawn without a callback`() = runComposeUiTest {
        var label = ""
        setContent {
            label = stringResource(Res.string.appearance_language)
            AppearanceUnderTest(AppearanceState(appLanguage = AppLanguage.Japanese, onAppLanguageChange = null))
        }

        onNode(hasText(label)).assertDoesNotExist()
    }

    @Test
    fun `the language row shows the stored choice`() = runComposeUiTest {
        var label = ""
        setContent {
            label = stringResource(Res.string.appearance_language)
            AppearanceUnderTest(AppearanceState(appLanguage = AppLanguage.Japanese, onAppLanguageChange = {}))
        }

        onNode(hasText(label) and hasText("日本語")).assertExists()
    }

    @Test
    fun `no language row is drawn while the setting is unknown`() = runComposeUiTest {
        var label = ""
        setContent {
            label = stringResource(Res.string.appearance_language)
            AppearanceUnderTest(AppearanceState(appLanguage = null, onAppLanguageChange = {}))
        }

        onNode(hasText(label)).assertDoesNotExist()
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

/** One option of the Language menu: a radio button, as in any single choice. */
private val isLanguageOption = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

@Composable
private fun AppearanceUnderTest(
    state: AppearanceState,
    navigation: RecordingNavigationController = RecordingNavigationController(),
) {
    CompositionLocalProvider(LocalNavigationController provides navigation) {
        AppearanceScreen(state)
    }
}
