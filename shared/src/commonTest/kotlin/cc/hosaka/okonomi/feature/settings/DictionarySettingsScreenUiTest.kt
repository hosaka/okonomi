package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.dictionary_settings_back
import okonomi.shared.generated.resources.dictionary_settings_learning
import org.jetbrains.compose.resources.stringResource

/**
 * "Learning", then 日本語 and English as one single choice. The option
 * labels are matched as literals on purpose: they are the languages' own
 * names and the same in every locale, so a test reading them back from
 * the resources would pass however they were spelled.
 */
@OptIn(ExperimentalTestApi::class)
class DictionarySettingsScreenUiTest : ComposeUiTestBase() {

    @Test
    fun `both languages are offered in their own names with japanese first`() = runComposeUiTest {
        var learning = ""
        setContent {
            learning = stringResource(Res.string.dictionary_settings_learning)
            DictionarySettingsUnderTest(
                DictionarySettingsState(learningLanguage = LearningLanguage.Japanese, onLearningLanguageChange = {}),
            )
        }

        onNodeWithText(learning).assertIsDisplayed()
        val japanese = onNodeWithText(JAPANESE)
        val english = onNodeWithText(ENGLISH)
        japanese.assertIsSelected()
        english.assertIsNotSelected()
        assertTrue(
            japanese.getBoundsInRoot().left < english.getBoundsInRoot().left,
            "日本語 comes before English",
        )
    }

    @Test
    fun `the language being learned is the one selected`() = runComposeUiTest {
        setContent {
            DictionarySettingsUnderTest(
                DictionarySettingsState(learningLanguage = LearningLanguage.English, onLearningLanguageChange = {}),
            )
        }

        onNodeWithText(ENGLISH).assertIsSelected()
        onNodeWithText(JAPANESE).assertIsNotSelected()
    }

    @Test
    fun `tapping the other language reports it`() = runComposeUiTest {
        val changes = mutableListOf<LearningLanguage>()
        setContent {
            DictionarySettingsUnderTest(
                DictionarySettingsState(learningLanguage = LearningLanguage.Japanese, onLearningLanguageChange = { changes += it }),
            )
        }

        onNodeWithText(ENGLISH).performClick()

        assertEquals(listOf(LearningLanguage.English), changes)
    }

    /**
     * The language already shown may be the device default rather than a
     * choice, and tapping it is not choosing it: written, it would stop
     * following the device.
     */
    @Test
    fun `tapping the language already chosen reports nothing`() = runComposeUiTest {
        val changes = mutableListOf<LearningLanguage>()
        setContent {
            DictionarySettingsUnderTest(
                DictionarySettingsState(learningLanguage = LearningLanguage.Japanese, onLearningLanguageChange = { changes += it }),
            )
        }

        onNodeWithText(JAPANESE).performClick()

        assertEquals(emptyList(), changes)
    }

    @Test
    fun `nothing is offered while the setting is unknown`() = runComposeUiTest {
        setContent {
            DictionarySettingsUnderTest(DictionarySettingsState())
        }

        assertEquals(0, onAllNodes(isSelectable()).fetchSemanticsNodes().size)
    }

    @Test
    fun `the choice is disabled without a callback`() = runComposeUiTest {
        setContent {
            DictionarySettingsUnderTest(DictionarySettingsState(learningLanguage = LearningLanguage.Japanese))
        }

        onNodeWithText(ENGLISH).assertIsNotEnabled()
    }

    @Test
    fun `the back button pops the screen`() = runComposeUiTest {
        var back = ""
        val navigation = RecordingNavigationController()
        setContent {
            back = stringResource(Res.string.dictionary_settings_back)
            DictionarySettingsUnderTest(DictionarySettingsState(), navigation)
        }

        onNodeWithContentDescription(back).performClick()

        assertEquals(1, navigation.pops)
    }
}

private const val JAPANESE = "日本語"

private const val ENGLISH = "English"

@Composable
private fun DictionarySettingsUnderTest(
    state: DictionarySettingsState,
    navigation: RecordingNavigationController = RecordingNavigationController(),
) {
    CompositionLocalProvider(LocalNavigationController provides navigation) {
        DictionarySettingsScreen(state)
    }
}
