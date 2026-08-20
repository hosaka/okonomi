package cc.hosaka.okonomi.feature.navigation.state

import cc.hosaka.okonomi.feature.search.SearchState
import cc.hosaka.okonomi.feature.search.searchScreenStateProducer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenStateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `persisted query survives a restart of the producer`() = runTest(dispatcher) {
        val viewModel = ScreenStateViewModel(
            initial = SearchState(),
        ) {
            searchScreenStateProducer()
        }

        val collector = launch { viewModel.state.collect {} }
        runCurrent()
        viewModel.state.first { it.onQueryChange != null }.onQueryChange!!.invoke("辞書")
        runCurrent()
        assertEquals("辞書", viewModel.state.value.query)
        collector.cancel()
        advanceTimeBy(6_000)

        val restarted = launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals("辞書", viewModel.state.first { it.onQueryChange != null }.query)
        restarted.cancel()
    }

    @Test
    fun `persisted flow is shared by key`() = runTest(dispatcher) {
        val viewModel = ScreenStateViewModel(
            initial = SearchState(),
        ) {
            searchScreenStateProducer()
        }

        val first = viewModel.mutablePersistedFlow("query", "")
        val second = viewModel.mutablePersistedFlow("query", "other")

        assertSame(first, second)
        assertEquals("", second.value)
    }
}
