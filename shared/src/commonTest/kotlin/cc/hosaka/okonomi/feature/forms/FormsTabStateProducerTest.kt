package cc.hosaka.okonomi.feature.forms

import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.lang.FormId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class FormsTabStateProducerTest {

    private val labels = mapOf(
        "v1" to "Ichidan verb",
        "v5u" to "Godan verb with 'u' ending",
        "v5u-s" to "Godan verb with 'u' ending (special class)",
    )

    private val neverLoad: suspend (List<String>) -> Map<String, String> = {
        throw AssertionError("an entry with nothing to conjugate has no class name to look up")
    }

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
    fun `a verb renders one table headed by the class name`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        var asked: List<String> = emptyList()

        val states = collectStates(
            scope.formsTabStateProducer(
                base = "食べる",
                posCodes = listOf("v1", "vt"),
                load = { codes ->
                    loads++
                    asked = codes
                    labels
                },
            ),
        )

        // Only the conjugable codes are looked up; vt names no paradigm.
        assertEquals(listOf("v1"), asked)
        val table = tablesOf(states.last()).single()
        assertEquals("Ichidan verb", table.className)
        assertEquals("食べる" to "食べない", table.forms.first().let { it.affirmative to it.negative })
        assertEquals(FormId.entries, table.forms.map { it.id })

        // A later run of the producer (the tab came back on screen)
        // reuses the persisted labels instead of querying again.
        collectStates(
            scope.formsTabStateProducer(base = "食べる", posCodes = listOf("v1", "vt"), load = { labels }),
        )
        assertEquals(1, loads)
    }

    /**
     * The point of B1: nothing here waits on the database. The table is
     * complete in the first emission, and the class name is the only
     * thing that arrives late.
     */
    @Test
    fun `the table is drawn before the class name arrives and upgrades in place`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.formsTabStateProducer(base = "食べる", posCodes = listOf("v1"), load = { labels }),
        )

        val headings = states.map { tablesOf(it).single().className }
        // Every emission carries the finished table; only the heading moves.
        assertTrue(states.all { tablesOf(it).single().forms.size == FormId.entries.size })
        assertEquals(listOf("v1", "Ichidan verb"), headings)
    }

    @Test
    fun `two conjugable classes render one table each in sense order`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.formsTabStateProducer(base = "厭う", posCodes = listOf("v5u", "v5u-s", "vt"), load = { labels }),
        )

        assertEquals(
            listOf("Godan verb with 'u' ending", "Godan verb with 'u' ending (special class)"),
            tablesOf(states.last()).map { it.className },
        )
    }

    @Test
    fun `a class the label table does not know is headed by its code`() = runTest {
        val scope = FakeScreenStateScope()

        // The dictionary can be generated without tag_label rows; a
        // table headed by "v1" beats one headed by nothing.
        val states = collectStates(
            scope.formsTabStateProducer(base = "食べる", posCodes = listOf("v1"), load = { emptyMap() }),
        )

        assertEquals("v1", tablesOf(states.last()).single().className)
    }

    /**
     * A heading is not worth a failure state. The table is already
     * correct and on screen, so a database failure must leave it there
     * — and must not drop the app-wide dictionary handle over a label.
     */
    @Test
    fun `a label read that fails leaves the table standing under its code`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.formsTabStateProducer(
                base = "食べる",
                posCodes = listOf("v1"),
                load = { throw RuntimeException("database gone") },
            ),
        )

        val table = tablesOf(states.last()).single()
        assertEquals("v1", table.className)
        assertEquals(FormId.entries, table.forms.map { it.id })
    }

    @Test
    fun `a suru noun is its own empty state reached without a query`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.formsTabStateProducer(base = "勉強", posCodes = listOf("n", "vs"), load = neverLoad),
        )

        assertEquals(FormsTabContentState.NotConjugable(takesSuru = true), states.last().content)
    }

    @Test
    fun `a plain noun gets the empty state without the suru wording`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.formsTabStateProducer(base = "本", posCodes = listOf("n"), load = neverLoad),
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
                    posCodes = listOf(if (base == "食") "v1" else "v5s"),
                    load = neverLoad,
                ),
            )
            assertEquals(FormsTabContentState.NotConjugable(takesSuru = false), states.last().content, base)
        }
    }
}
