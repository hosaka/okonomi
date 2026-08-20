package cc.hosaka.okonomi.feature.search

import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import kotlinx.coroutines.flow.MutableStateFlow
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

private class FakeScreenStateScope : ScreenStateScope {
    private val persisted = mutableMapOf<String, MutableStateFlow<*>>()

    val navigated = mutableListOf<Route>()

    override val navigation: NavigationController = object : NavigationController {
        override fun navigate(route: Route) {
            navigated += route
        }

        override fun pop(): Boolean = false
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> mutablePersistedFlow(
        key: String,
        initial: T,
    ): MutableStateFlow<T> = persisted
        .getOrPut(key) { MutableStateFlow(initial) } as MutableStateFlow<T>
}
