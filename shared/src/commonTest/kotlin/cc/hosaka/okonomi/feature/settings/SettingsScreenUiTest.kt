package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.settings_about_description
import okonomi.shared.generated.resources.settings_about_title
import okonomi.shared.generated.resources.settings_appearance_description
import okonomi.shared.generated.resources.settings_appearance_title
import okonomi.shared.generated.resources.settings_dictionary_category_description
import okonomi.shared.generated.resources.settings_dictionary_category_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Settings root lists its categories, and each row, as a whole,
 * pushes its own screen. The clicks target the description text with a
 * click action, so a description shown outside the clickable row would
 * not find a node.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest : ComposeUiTestBase() {
    @Test
    fun `all three categories are listed with their descriptions`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            SettingsUnderTest(RecordingNavigationController())
        }

        onNodeWithText(labels.appearance).assertIsDisplayed()
        onNodeWithText(labels.appearanceDescription).assertIsDisplayed()
        onNodeWithText(labels.dictionary).assertIsDisplayed()
        onNodeWithText(labels.dictionaryDescription).assertIsDisplayed()
        onNodeWithText(labels.about).assertIsDisplayed()
        onNodeWithText(labels.aboutDescription).assertIsDisplayed()
        onAllNodes(hasClickAction()).assertCountEquals(3)
    }

    @Test
    fun `tapping dictionary pushes the dictionary screen`() = runComposeUiTest {
        val labels = Labels()
        val navigation = RecordingNavigationController()
        setContent {
            labels.read()
            SettingsUnderTest(navigation)
        }

        onNode(hasText(labels.dictionaryDescription) and hasClickAction()).performClick()

        assertEquals(listOf<Any>(DictionarySettingsRoute), navigation.navigated)
    }

    @Test
    fun `tapping appearance pushes the appearance screen`() = runComposeUiTest {
        val labels = Labels()
        val navigation = RecordingNavigationController()
        setContent {
            labels.read()
            SettingsUnderTest(navigation)
        }

        onNode(hasText(labels.appearanceDescription) and hasClickAction()).performClick()

        assertEquals(listOf<Any>(AppearanceRoute), navigation.navigated)
    }

    @Test
    fun `tapping about pushes the about screen`() = runComposeUiTest {
        val labels = Labels()
        val navigation = RecordingNavigationController()
        setContent {
            labels.read()
            SettingsUnderTest(navigation)
        }

        onNode(hasText(labels.aboutDescription) and hasClickAction()).performClick()

        assertEquals(listOf<Any>(AboutRoute), navigation.navigated)
    }
}

@Composable
private fun SettingsUnderTest(navigation: RecordingNavigationController) {
    CompositionLocalProvider(LocalNavigationController provides navigation) {
        SettingsScreen()
    }
}

private class Labels {
    var appearance = ""
    var appearanceDescription = ""
    var dictionary = ""
    var dictionaryDescription = ""
    var about = ""
    var aboutDescription = ""

    @Composable
    fun read() {
        appearance = stringResource(Res.string.settings_appearance_title)
        appearanceDescription = stringResource(Res.string.settings_appearance_description)
        dictionary = stringResource(Res.string.settings_dictionary_category_title)
        dictionaryDescription = stringResource(Res.string.settings_dictionary_category_description)
        about = stringResource(Res.string.settings_about_title)
        aboutDescription = stringResource(Res.string.settings_about_description)
    }
}
