package cc.hosaka.okonomi.feature.libraries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.License
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.libraries_empty
import okonomi.shared.generated.resources.libraries_error
import org.jetbrains.compose.resources.stringResource

private const val WEBSITE = "https://example.org/library"

private const val LICENCE_URL = "https://spdx.org/licenses/Apache-2.0.html"

/**
 * No test composed this screen at all, so the credits list could ship
 * blank — or lose the attribution its rows exist to carry — with the
 * whole suite green.
 *
 * The rows are AboutLibraries' own again (the hand-written list was
 * reverted once a release build showed the container was never the
 * slowdown), so these no longer assert our own layout. They assert the
 * two things that are ours to guarantee: that the screen renders at all
 * in each of its states, and that a reader can reach a library's licence
 * terms and its home page.
 *
 * "Website" and "View license" are AboutLibraries' own captions, not
 * strings we own. Asserting on them couples these tests to the library's
 * wording — deliberately: if a version bump renames or removes those
 * actions, the attribution path has changed and that is exactly the
 * change we want to be told about.
 */
@OptIn(ExperimentalTestApi::class)
class LibrariesScreenUiTest : ComposeUiTestBase() {

    @Test
    fun `every library gets a row with its name and version and licence`() = runComposeUiTest {
        setContent {
            LibrariesUnderTest(libraries = Loadable.Ok(twoLibraries()))
        }

        // Refined merges each row's descendants, so the row's own text
        // is the whole line: name, version and licence name together.
        onNodeWithText("alpha").performScrollTo().assertIsDisplayed()
        onNodeWithText("beta").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("1.0.0", substring = true).onFirst().assertIsDisplayed()
        onAllNodesWithText("Apache-2.0", substring = true).onFirst().assertIsDisplayed()
    }

    /**
     * The attribution guarantee, and the one thing on this screen that
     * must never regress. The licence action opens the terms URL through
     * the platform's [UriHandler] rather than rendering bundled licence
     * text — verified against AboutLibraries 15.0.4 rather than assumed.
     */
    @Test
    fun `a library's licence action opens its terms`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            LibrariesUnderTest(libraries = Loadable.Ok(twoLibraries()), opened = opened)
        }

        // Refined's detail is inline: the row expands to reveal its
        // actions rather than opening a dialog.
        onNodeWithText("alpha").performScrollTo().performClick()
        onAllNodesWithText("View license").onFirst().performClick()

        assertEquals(listOf(LICENCE_URL), opened, "the licence must reach the terms")
    }

    @Test
    fun `a library's website action opens its page`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            LibrariesUnderTest(libraries = Loadable.Ok(twoLibraries()), opened = opened)
        }

        onNodeWithText("alpha").performScrollTo().performClick()
        onAllNodesWithText("Website").onFirst().performClick()

        assertEquals(listOf(WEBSITE), opened)
    }

    @Test
    fun `a list that could not be loaded says so instead of showing nothing`() = runComposeUiTest {
        lateinit var error: String
        setContent {
            error = stringResource(Res.string.libraries_error)
            LibrariesUnderTest(libraries = Loadable.Ok(null))
        }

        onNodeWithText(error).assertIsDisplayed()
    }

    @Test
    fun `an empty list says so rather than rendering a blank screen`() = runComposeUiTest {
        lateinit var empty: String
        setContent {
            empty = stringResource(Res.string.libraries_empty)
            LibrariesUnderTest(libraries = Loadable.Ok(libsOf()))
        }

        onNodeWithText(empty).assertIsDisplayed()
    }
}

/**
 * Both fixture libraries carry the same licence, as the 130 Apache-2.0
 * entries of the real manifest do, and carry both a URL and licence text
 * — the shape the export actually produces.
 */
private fun twoLibraries(): Libs = libsOf(
    "org.example:alpha",
    "org.example:beta",
    website = WEBSITE,
    licenses = setOf(
        License(
            name = "Apache-2.0",
            url = LICENCE_URL,
            year = null,
            spdxId = "Apache-2.0",
            licenseContent = "Apache License\nVersion 2.0, January 2004",
            hash = "Apache-2.0",
        ),
    ),
)

@Composable
private fun LibrariesUnderTest(
    libraries: Loadable<Libs?>,
    opened: MutableList<String> = mutableListOf(),
) {
    CompositionLocalProvider(
        LocalNavigationController provides RecordingNavigationController(),
        LocalUriHandler provides RecordingUriHandler(opened),
    ) {
        LibrariesScreen(LibrariesState(libraries = libraries))
    }
}

private class RecordingUriHandler(private val opened: MutableList<String>) : UriHandler {
    override fun openUri(uri: String) {
        opened += uri
    }
}
