package cc.hosaka.okonomi.ui.labels

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.DictionaryTag
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.forms.FormsTab
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.search.SearchResultsState
import cc.hosaka.okonomi.feature.search.SearchScreen
import cc.hosaka.okonomi.feature.search.SearchState
import cc.hosaka.okonomi.feature.word.WordTab
import cc.hosaka.okonomi.lang.AppLanguage
import cc.hosaka.okonomi.lang.ProvideAppLanguage
import cc.hosaka.okonomi.lang.forgetAppliedAppLanguage
import cc.hosaka.okonomi.lang.setAppLanguage
import cc.hosaka.okonomi.lang.snapshotPhoneLanguage
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.test.entryDetail
import cc.hosaka.okonomi.ui.test.entrySense
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.robolectric.annotation.Config

/**
 * The dictionary's tags and the deinflector's rules as the reader sees
 * them: the Word tab's chips, the Forms tab's class heading and the
 * search row's breadcrumb, in a Japanese UI, in an English one, and
 * across a switch from one to the other.
 *
 * The expected text is written out rather than read through
 * `stringResource`, which would follow the very locale under test. The
 * English is the app's own wording in `values/tags.xml`, lowercased on
 * the chips and the heading. The fixtures' dictionary labels are worded
 * differently from it on purpose, so a chip drawn from the label rather
 * than from the app's string fails in English too, and the Forms table
 * carries no label at all. A phone is Robolectric's qualifiers, en-US
 * without any; the switch applies a language to the whole process, so
 * every test puts the phone's language back and forgets the applied
 * one, as `ProvideAppLanguageUiTest` does.
 */
@OptIn(ExperimentalTestApi::class)
class DictionaryLabelsUiTest : ComposeUiTestBase() {

    private var restorePhoneLanguage: () -> Unit = {}

    @BeforeTest
    fun startCold() {
        restorePhoneLanguage = snapshotPhoneLanguage()
        forgetAppliedAppLanguage()
    }

    @AfterTest
    fun putThePhoneLanguageBack() {
        restorePhoneLanguage()
        forgetAppliedAppLanguage()
    }

    @Test
    @Config(qualifiers = "ja")
    fun `a japanese ui draws the word tab chips in japanese`() = runComposeUiTest {
        setContent { WordTabUnderTest(tags = listOf(GODAN_KU, USUALLY_KANA)) }

        onNodeWithText("五段動詞（カ行）").assertExists()
        onNodeWithText("通常仮名で表記").assertExists()
        onNodeWithText(ENGLISH_GODAN_KU).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ja")
    fun `a japanese ui heads the forms table in japanese`() = runComposeUiTest {
        setContent { FormsTabUnderTest() }

        onNodeWithText(JAPANESE_ICHIDAN).assertExists()
        onNodeWithText(ENGLISH_ICHIDAN).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ja")
    fun `a japanese ui draws the breadcrumb rule in japanese and the suffix as it is`() = runComposeUiTest {
        setContent { BreadcrumbUnderTest(traceLabels = listOf("negative", "-た")) }

        onNodeWithText("‹ 否定形, -た").assertExists()
    }

    /**
     * A code a later dictionary adds has no string here, and is drawn as
     * the dictionary's own label, lowercased like any chip: not as its
     * bare code, and not as nothing.
     */
    @Test
    @Config(qualifiers = "ja")
    fun `a japanese ui draws a tag with no string as the dictionary label`() = runComposeUiTest {
        setContent { WordTabUnderTest(tags = listOf(UNKNOWN_TAG)) }

        onNodeWithText("new label").assertExists()
    }

    @Test
    @Config(qualifiers = "ja")
    fun `a japanese ui draws a rule with no string as its name`() = runComposeUiTest {
        setContent { BreadcrumbUnderTest(traceLabels = listOf("brand-new")) }

        onNodeWithText("‹ brand-new").assertExists()
    }

    @Test
    fun `an english ui draws the word tab chips in the app's english`() = runComposeUiTest {
        setContent { WordTabUnderTest(tags = listOf(GODAN_KU, USUALLY_KANA, UNKNOWN_TAG)) }

        onNodeWithText(ENGLISH_GODAN_KU).assertExists()
        onNodeWithText("word usually written using kana alone").assertExists()
        onNodeWithText(GODAN_KU.label.lowercase()).assertDoesNotExist()
        onNodeWithText(USUALLY_KANA.label.lowercase()).assertDoesNotExist()
        // The one tag with no string of its own: its label is all there is.
        onNodeWithText("new label").assertExists()
    }

    @Test
    fun `an english ui heads the forms table in the app's english`() = runComposeUiTest {
        setContent { FormsTabUnderTest() }

        onNodeWithText(ENGLISH_ICHIDAN).assertExists()
    }

    @Test
    fun `an english ui draws the breadcrumb as before`() = runComposeUiTest {
        setContent { BreadcrumbUnderTest(traceLabels = listOf("negative", "-た", "brand-new")) }

        onNodeWithText("‹ negative, -た, brand-new").assertExists()
    }

    /**
     * Live, with no reload: the chips are resolved where they are drawn,
     * so they redraw when [ProvideAppLanguage] recomposes them. A label
     * resolved once, in state or a producer, would keep the language it
     * was resolved in.
     */
    @Test
    fun `switching to japanese redraws the word tab chips in place`() = runComposeUiTest {
        val preferences = FakePreferenceStore()
        setContent {
            ProvideAppLanguage(preferences) { WordTabUnderTest(tags = listOf(GODAN_KU)) }
        }
        waitForIdle()
        onNodeWithText(ENGLISH_GODAN_KU).assertExists()

        preferences.setAppLanguage(AppLanguage.Japanese)
        waitForIdle()

        onNodeWithText("五段動詞（カ行）").assertExists()
        onNodeWithText(ENGLISH_GODAN_KU).assertDoesNotExist()
    }

    @Test
    fun `switching to japanese redraws the forms heading in place`() = runComposeUiTest {
        val preferences = FakePreferenceStore()
        setContent {
            ProvideAppLanguage(preferences) { FormsTabUnderTest() }
        }
        waitForIdle()
        onNodeWithText(ENGLISH_ICHIDAN).assertExists()

        preferences.setAppLanguage(AppLanguage.Japanese)
        waitForIdle()

        onNodeWithText(JAPANESE_ICHIDAN).assertExists()
        onNodeWithText(ENGLISH_ICHIDAN).assertDoesNotExist()
    }

    @Test
    fun `switching to japanese redraws the breadcrumb in place`() = runComposeUiTest {
        val preferences = FakePreferenceStore()
        setContent {
            ProvideAppLanguage(preferences) { BreadcrumbUnderTest(traceLabels = listOf("negative", "-た")) }
        }
        waitForIdle()
        onNodeWithText("‹ negative, -た").assertExists()

        preferences.setAppLanguage(AppLanguage.Japanese)
        waitForIdle()

        onNodeWithText("‹ 否定形, -た").assertExists()
        onNodeWithText("‹ negative, -た").assertDoesNotExist()
    }
}

/**
 * Tags as `loadEntryDetail` carries them, with dictionary labels worded
 * unlike the app's strings for the same codes: a chip that shows the
 * label instead of the app's string fails in either language.
 */
private val GODAN_KU = DictionaryTag(code = "v5k", label = "Older godan wording")
private val USUALLY_KANA = DictionaryTag(code = "uk", label = "Older kana-alone wording")
private val UNKNOWN_TAG = DictionaryTag(code = "zz-new", label = "New label")

private const val ENGLISH_GODAN_KU = "godan verb with 'ku' ending"
private const val ENGLISH_ICHIDAN = "ichidan verb"
private const val JAPANESE_ICHIDAN = "一段動詞"

@Composable
private fun WordTabUnderTest(tags: List<DictionaryTag>) {
    WordTab(
        entry = entryDetail(
            headword = "書く",
            forms = listOf(EntryForm("書く", isCommon = true)),
            readings = listOf(EntryReading("かく", emptyList(), isCommon = true)),
            senses = listOf(entrySense(posCodes = listOf("v5k"), tags = tags, glosses = listOf("to write"))),
        ),
        contentPadding = PaddingValues(),
    )
}

/**
 * 食べる's Forms tab. Nothing in it reads the dictionary: the table
 * carries its class's bare code, so a heading that reads as a name can
 * only be the app's own string.
 */
@Composable
private fun FormsTabUnderTest() {
    ScreenHost {
        FormsTab(
            entry = entryDetail(
                headword = "食べる",
                forms = listOf(EntryForm("食べる", isCommon = true)),
                readings = listOf(EntryReading("たべる", emptyList(), isCommon = true)),
                senses = listOf(entrySense(posCodes = listOf("v1"), glosses = listOf("to eat"))),
            ),
            contentPadding = PaddingValues(),
        )
    }
}

/** 食べる found from 食べなかった, or whatever trace a test names. */
@Composable
private fun BreadcrumbUnderTest(traceLabels: List<String>) {
    CompositionLocalProvider(LocalNavigationController provides RecordingNavigationController()) {
        SearchScreen(
            SearchState(
                query = "たべなかった",
                results = SearchResultsState.Results(
                    query = "たべなかった",
                    hits = listOf(
                        SearchHit(
                            entryId = 1_358_280L,
                            titleSegments = listOf(
                                TitleSegment(text = "食べる"),
                                TitleSegment(text = "たべる", readsPreviousSegment = true),
                            ),
                            traceLabels = traceLabels,
                            senseLines = listOf("to eat"),
                            isCommon = false,
                        ),
                    ),
                    isFallback = false,
                ),
            ),
        )
    }
}
