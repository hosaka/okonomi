package cc.hosaka.okonomi.feature.forms

import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.lang.FormId
import cc.hosaka.okonomi.ui.furigana.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class FormsTabStateProducerTest {

    private fun TestScope.collectStates(flow: Flow<FormsTabState>): List<FormsTabState> {
        val states = mutableListOf<FormsTabState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { states += it }
        }
        runCurrent()
        return states
    }

    private fun tablesOf(state: FormsTabState) =
        assertIs<FormsTabContentState.Ready>(state.content).tables

    @Test
    fun `a verb renders one table under its class code`() = runTest {
        val states = collectStates(
            FakeScreenStateScope().formsTabStateProducer(base = "食べる", reading = "たべる", posCodes = listOf("v1", "vt")),
        )

        // vt names no paradigm, so it heads no table.
        val table = tablesOf(states.last()).single()
        assertEquals("v1", table.classCode)
        assertEquals(
            "食べる" to "食べない",
            table.rows.first().let { it.affirmative.plainText() to it.negative?.plainText() },
        )
        assertEquals(FormId.entries, table.rows.map { it.id })
    }

    @Test
    fun `two conjugable classes render one table each in sense order`() = runTest {
        val states = collectStates(
            FakeScreenStateScope().formsTabStateProducer(
                base = "厭う",
                reading = "いとう",
                posCodes = listOf("v5u", "v5u-s", "vt"),
            ),
        )

        assertEquals(listOf("v5u", "v5u-s"), tablesOf(states.last()).map { it.classCode })
    }

    @Test
    fun `a suru noun is its own empty state`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.formsTabStateProducer(base = "勉強", reading = "べんきょう", posCodes = listOf("n", "vs")),
        )

        assertEquals(FormsTabContentState.NotConjugable(takesSuru = true), states.last().content)
    }

    @Test
    fun `a plain noun gets the empty state without the suru wording`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.formsTabStateProducer(base = "本", reading = "ほん", posCodes = listOf("n")),
        )

        assertEquals(FormsTabContentState.NotConjugable(takesSuru = false), states.last().content)
    }

    @Test
    fun `a headword that disagrees with its code falls to the empty state`() = runTest {
        val scope = FakeScreenStateScope()

        // A kanji form short of its okurigana, and JMdict's 画餅に帰する,
        // tagged v5s while ending in る.
        for (base in listOf("食", "画餅に帰する")) {
            val states = collectStates(
                scope.formsTabStateProducer(
                    base = base,
                    reading = null,
                    posCodes = listOf(if (base == "食") "v1" else "v5s"),
                ),
            )
            assertEquals(FormsTabContentState.NotConjugable(takesSuru = false), states.last().content, base)
        }
    }
}
