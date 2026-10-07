package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.about_back
import org.jetbrains.compose.resources.stringResource

/**
 * The EDRDG licence requires the conformance statement to be displayed, and a
 * reviewer showed that deleting the whole `CreditsSection(...)` call from
 * [AboutScreen] left every test green. These assert the section is actually
 * rendered, through the real screen rather than the section in isolation.
 */
@OptIn(ExperimentalTestApi::class)
class AboutCreditsUiTest : ComposeUiTestBase() {
    @Test
    fun `the about screen displays the EDRDG conformance statement`() = runComposeUiTest {
        lateinit var statement: String
        setContent {
            statement = stringResource(edrdgStatement)
            AboutUnderTest()
        }

        onNodeWithText(statement).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `every credited source gets its own row`() = runComposeUiTest {
        setContent {
            AboutUnderTest()
        }

        creditEntries.forEach { entry ->
            onNodeWithText(entry.name).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `a credit row shows the licence it is used under`() = runComposeUiTest {
        setContent {
            AboutUnderTest()
        }

        onNodeWithText("GPL-3.0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the back button pops the screen`() = runComposeUiTest {
        var back = ""
        val navigation = RecordingNavigationController()
        setContent {
            back = stringResource(Res.string.about_back)
            AboutUnderTest(navigation = navigation)
        }

        onNodeWithContentDescription(back).performClick()

        assertEquals(1, navigation.pops)
    }
}

/**
 * The dictionary row is left loading on purpose: its value string also starts
 * with "JMdict", which would make the credit row of that name ambiguous.
 */
private fun aboutState(
    dictionary: Loadable<DictionaryInfo?> = Loadable.Loading,
) = AboutState(
    dictionary = dictionary,
)

@Composable
private fun AboutUnderTest(
    state: AboutState = aboutState(),
    navigation: NavigationController = RecordingNavigationController(),
) {
    CompositionLocalProvider(LocalNavigationController provides navigation) {
        AboutScreen(state)
    }
}
