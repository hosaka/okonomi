package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import org.jetbrains.compose.resources.stringResource

/**
 * The EDRDG licence requires the conformance statement to be displayed, and a
 * reviewer showed that deleting the whole `CreditsSection(...)` call from
 * [SettingsScreen] left every test green. These assert the section is actually
 * rendered, through the real screen rather than the section in isolation.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsCreditsUiTest : ComposeUiTestBase() {
    @Test
    fun `the settings screen displays the EDRDG conformance statement`() = runComposeUiTest {
        lateinit var statement: String
        setContent {
            statement = stringResource(edrdgStatement)
            SettingsUnderTest()
        }

        onNodeWithText(statement).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `every credited source gets its own row`() = runComposeUiTest {
        setContent {
            SettingsUnderTest()
        }

        creditEntries.forEach { entry ->
            onNodeWithText(entry.name).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `a credit row shows the licence it is used under`() = runComposeUiTest {
        setContent {
            SettingsUnderTest()
        }

        onNodeWithText("GPL-3.0").performScrollTo().assertIsDisplayed()
    }
}

/**
 * The dictionary row is left loading on purpose: its value string also starts
 * with "JMdict", which would make the credit row of that name ambiguous.
 */
private fun settingsState(
    dictionary: Loadable<DictionaryInfo?> = Loadable.Loading,
) = SettingsState(
    dictionary = dictionary,
)

@Composable
private fun SettingsUnderTest(
    state: SettingsState = settingsState(),
    navigation: NavigationController = RecordingNavigationController(),
) {
    CompositionLocalProvider(LocalNavigationController provides navigation) {
        SettingsScreen(state)
    }
}
