package cc.hosaka.okonomi.feature.settings

import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStateProducerTest {
    private val dispatcher = StandardTestDispatcher()

    private val dictionaryInfo = DictionaryInfo(jmdictDate = "2026-08-21", entryCount = 42L)

    private suspend fun FakeScreenStateScope.producer(
        loadDictionary: suspend () -> DictionaryInfo = { dictionaryInfo },
    ): Flow<SettingsState> = settingsScreenStateProducer(loadDictionary)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `screen state is loading until the loader completes`() = runTest(dispatcher) {
        val load = CompletableDeferred<DictionaryInfo>()
        val viewModel = ScreenStateViewModel(
            initial = SettingsState(),
        ) {
            settingsScreenStateProducer(
                loadDictionary = { load.await() },
            )
        }

        val collector = launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(Loadable.Loading, viewModel.state.value.dictionary)

        load.complete(dictionaryInfo)
        runCurrent()
        assertEquals(Loadable.Ok(dictionaryInfo), viewModel.state.value.dictionary)
        collector.cancel()
    }

    @Test
    fun `the dictionary info is loaded and kept between runs of the producer`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        val loadDictionary: suspend () -> DictionaryInfo = {
            loads++
            dictionaryInfo
        }

        assertEquals(
            Loadable.Ok(dictionaryInfo),
            scope.producer(loadDictionary = loadDictionary)
                .first { it.dictionary !is Loadable.Loading }.dictionary,
        )
        assertEquals(
            Loadable.Ok(dictionaryInfo),
            scope.producer(loadDictionary = loadDictionary)
                .first { it.dictionary !is Loadable.Loading }.dictionary,
        )
        assertEquals(1, loads)
    }

    @Test
    fun `a failing dictionary load shows nothing and is retried on the next run`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        val loadDictionary: suspend () -> DictionaryInfo = {
            loads++
            error("no dictionary")
        }

        val state = scope.producer(loadDictionary = loadDictionary)
            .first { it.dictionary !is Loadable.Loading }
        assertEquals(Loadable.Ok(null), state.dictionary)
        assertEquals(Loadable.Loading, scope.dictionarySink().value)

        scope.producer(loadDictionary = loadDictionary).first { it.dictionary !is Loadable.Loading }
        assertEquals(2, loads)
    }
}

private fun FakeScreenStateScope.dictionarySink() =
    mutablePersistedFlow<Loadable<DictionaryInfo?>>("dictionary", Loadable.Loading)
