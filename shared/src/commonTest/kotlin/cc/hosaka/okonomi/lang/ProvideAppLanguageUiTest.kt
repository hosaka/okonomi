package cc.hosaka.okonomi.lang

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/**
 * [ProvideAppLanguage] on every platform, the iOS lane included: what it
 * holds back until the language is known, and what a chosen language
 * does and does not change.
 *
 * Japanese is asserted as the written-out 設定 rather than through
 * `stringResource`, which would follow the very locale under test. No
 * phone language can be set from common code, so the phone is whatever
 * the machine is, and each test puts it back afterwards, along with the
 * language the process last applied, so every test starts cold.
 */
@OptIn(ExperimentalTestApi::class)
class ProvideAppLanguageUiTest : ComposeUiTestBase() {

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

    /**
     * A cold start: nothing is known, so nothing is drawn, rather than a
     * frame in the phone's language. The answer is System default, which
     * leaves the process language as it was.
     */
    @Test
    fun `nothing is drawn until the store answers`() = runComposeUiTest {
        val preferences = UnansweredStringStore()
        var compositions = 0
        setContent {
            ProvideAppLanguage(preferences) {
                compositions++
                Text(CONTENT)
            }
        }
        waitForIdle()

        onNodeWithText(CONTENT).assertDoesNotExist()
        assertEquals(0, compositions, "the content was composed before the language was known")

        preferences.answer(AppLanguage.System.storedValue)
        waitForIdle()

        onNodeWithText(CONTENT).assertExists()
    }

    /**
     * What a rotation does: the activity and every provider in it are
     * composed anew, in a process that has applied a language already.
     * The new provider draws at once, in that language, before its own
     * store has answered.
     */
    @Test
    fun `a provider composed after a language was applied draws at once in that language`() = runComposeUiTest {
        val unanswered = UnansweredStringStore()
        var recreated by mutableStateOf(false)
        setContent {
            if (!recreated) {
                ProvideAppLanguage(storedLanguage("ja")) { Text(stringResource(Res.string.settings_title)) }
            } else {
                ProvideAppLanguage(unanswered) { Text(stringResource(Res.string.settings_title)) }
            }
        }
        waitForIdle()
        onNodeWithText(JAPANESE_SETTINGS).assertExists()

        recreated = true
        waitForIdle()

        onNodeWithText(JAPANESE_SETTINGS).assertExists()
    }

    /**
     * The override reaches the strings, and stops there: the phone's
     * language, which the learning language's default is taken from,
     * reads the same under it as before it.
     */
    @Test
    fun `a stored japanese draws in japanese and leaves the phone language alone`() = runComposeUiTest {
        val phoneLanguage = currentDeviceLanguage()
        setContent {
            ProvideAppLanguage(storedLanguage("ja")) { Text(stringResource(Res.string.settings_title)) }
        }
        waitForIdle()

        onNodeWithText(JAPANESE_SETTINGS).assertExists()
        assertEquals(phoneLanguage, currentDeviceLanguage())
    }
}

private const val CONTENT = "content"
private const val JAPANESE_SETTINGS = "設定"

private fun storedLanguage(value: String) = FakePreferenceStore(strings = mapOf(APP_LANGUAGE_PREFERENCE to value))

/** A store whose string reads emit nothing until [answer] is called. */
private class UnansweredStringStore : PreferenceStore {
    private val stored = MutableStateFlow<String?>(null)

    fun answer(value: String) {
        stored.value = value
    }

    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = flowOf(default)

    override fun setBoolean(key: String, value: Boolean) = Unit

    override fun stringFlow(key: String, default: String): Flow<String> = stored.filterNotNull()

    override fun setString(key: String, value: String) {
        stored.value = value
    }
}
