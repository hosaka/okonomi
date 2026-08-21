package cc.hosaka.okonomi.feature.settings

import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateViewModel
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
        loadLibraries: suspend () -> Libs = { libsOf("org.example:library") },
        loadDictionary: suspend () -> DictionaryInfo = { dictionaryInfo },
    ): Flow<SettingsState> = settingsScreenStateProducer(loadLibraries, loadDictionary)

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
        val libs = libsOf("org.example:library")
        val load = CompletableDeferred<Libs>()
        val viewModel = ScreenStateViewModel(
            initial = SettingsState(),
        ) {
            settingsScreenStateProducer(
                loadLibraries = { load.await() },
                loadDictionary = { dictionaryInfo },
            )
        }

        val collector = launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(Loadable.Loading, viewModel.state.value.libraries)

        load.complete(libs)
        runCurrent()
        assertEquals(Loadable.Ok(libs), viewModel.state.value.libraries)
        assertEquals(Loadable.Ok(dictionaryInfo), viewModel.state.value.dictionary)
        collector.cancel()
    }

    @Test
    fun `the libraries render without waiting for the dictionary load`() = runTest {
        val scope = FakeScreenStateScope()
        val dictionaryGate = CompletableDeferred<DictionaryInfo>()

        val state = scope.producer(
            loadDictionary = { dictionaryGate.await() },
        ).first { it.libraries is Loadable.Ok }

        assertEquals(Loadable.Loading, state.dictionary)
    }

    @Test
    fun `a failing loader yields an empty list and is retried on the next run`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        val loadLibraries: suspend () -> Libs = {
            loads++
            error("no resource")
        }

        val state = scope.producer(loadLibraries = loadLibraries)
            .first { it.libraries !is Loadable.Loading }
        assertEquals(Loadable.Ok(emptyLibs()), state.libraries)
        assertEquals(Loadable.Loading, scope.librariesSink().value)

        scope.producer(loadLibraries = loadLibraries).first { it.libraries !is Loadable.Loading }
        assertEquals(2, loads)
    }

    @Test
    fun `the loaded libraries are kept between runs of the producer`() = runTest {
        val scope = FakeScreenStateScope()
        val libs = libsOf("org.example:library")
        var loads = 0
        val loadLibraries: suspend () -> Libs = {
            loads++
            libs
        }

        assertEquals(
            Loadable.Ok(libs),
            scope.producer(loadLibraries = loadLibraries).first { it.libraries is Loadable.Ok }.libraries,
        )
        assertEquals(
            Loadable.Ok(libs),
            scope.producer(loadLibraries = loadLibraries).first { it.libraries is Loadable.Ok }.libraries,
        )
        assertEquals(1, loads)
    }

    @Test
    fun `cancelling while loading leaves the sink loading and retries on the next run`() = runTest {
        val scope = FakeScreenStateScope()
        val libs = libsOf("org.example:library")
        var loads = 0

        val collector = launch {
            scope.producer(
                loadLibraries = {
                    loads++
                    awaitCancellation()
                },
            ).first { it.libraries is Loadable.Ok }
        }
        runCurrent()
        collector.cancel()
        runCurrent()
        assertEquals(Loadable.Loading, scope.librariesSink().value)
        assertEquals(1, loads)

        val state = scope.producer(
            loadLibraries = {
                loads++
                libs
            },
        ).first { it.libraries is Loadable.Ok }
        assertEquals(Loadable.Ok(libs), state.libraries)
        assertEquals(2, loads)
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

private fun FakeScreenStateScope.librariesSink() =
    mutablePersistedFlow<Loadable<Libs>>("libraries", Loadable.Loading)

private fun FakeScreenStateScope.dictionarySink() =
    mutablePersistedFlow<Loadable<DictionaryInfo?>>("dictionary", Loadable.Loading)

private fun emptyLibs(): Libs = Libs(
    libraries = emptyList(),
    licenses = emptySet(),
)

private fun libsOf(vararg uniqueIds: String): Libs = Libs(
    libraries = uniqueIds.map { uniqueId ->
        Library(
            uniqueId = uniqueId,
            artifactVersion = "1.0.0",
            name = uniqueId.substringAfter(':'),
            description = null,
            website = null,
            developers = emptyList(),
            organization = null,
            scm = null,
        )
    },
    licenses = emptySet(),
)
