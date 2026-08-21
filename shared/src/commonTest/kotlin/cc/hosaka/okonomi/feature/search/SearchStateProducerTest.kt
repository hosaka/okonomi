package cc.hosaka.okonomi.feature.search

import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class SearchStateProducerTest {
    @Test
    fun `starts with an empty query and no clear action`() = runTest {
        val scope = FakeScreenStateScope()

        val state = scope.searchScreenStateProducer().first()

        assertEquals("", state.query)
        assertNotNull(state.onQueryChange)
        assertNull(state.onClear)
    }

    @Test
    fun `typing updates the query and enables clear`() = runTest {
        val scope = FakeScreenStateScope()
        val flow = scope.searchScreenStateProducer()

        flow.first().onQueryChange!!.invoke("こんにちは")
        val state = flow.first()

        assertEquals("こんにちは", state.query)
        assertNotNull(state.onClear)
    }

    @Test
    fun `clear empties the query and hides clear`() = runTest {
        val scope = FakeScreenStateScope()
        val flow = scope.searchScreenStateProducer()

        flow.first().onQueryChange!!.invoke("こんにちは")
        flow.first().onClear!!.invoke()
        val state = flow.first()

        assertEquals("", state.query)
        assertNull(state.onClear)
    }

    @Test
    fun `query is kept in the persisted flow`() = runTest {
        val scope = FakeScreenStateScope()
        val flow = scope.searchScreenStateProducer()

        flow.first().onQueryChange!!.invoke("辞書")

        val persisted = scope.mutablePersistedFlow("query", "")
        assertEquals("辞書", persisted.value)
        assertSame(persisted, scope.mutablePersistedFlow("query", "other"))
    }
}
