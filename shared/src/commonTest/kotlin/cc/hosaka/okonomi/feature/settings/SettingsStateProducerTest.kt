package cc.hosaka.okonomi.feature.settings

import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateViewModel
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
            settingsScreenStateProducer(loadLibraries = { load.await() })
        }

        val collector = launch { viewModel.state.collect {} }
        runCurrent()
        assertEquals(Loadable.Loading, viewModel.state.value.libraries)

        load.complete(libs)
        runCurrent()
        assertEquals(Loadable.Ok(libs), viewModel.state.value.libraries)
        collector.cancel()
    }

    @Test
    fun `a failing loader yields an empty list and is retried on the next run`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        val loadLibraries: suspend () -> Libs = {
            loads++
            error("no resource")
        }

        val state = scope.settingsScreenStateProducer(loadLibraries).first()
        assertEquals(Loadable.Ok(emptyLibs()), state.libraries)
        assertEquals(Loadable.Loading, scope.librariesSink().value)

        scope.settingsScreenStateProducer(loadLibraries).first()
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

        assertEquals(Loadable.Ok(libs), scope.settingsScreenStateProducer(loadLibraries).first().libraries)
        assertEquals(Loadable.Ok(libs), scope.settingsScreenStateProducer(loadLibraries).first().libraries)
        assertEquals(1, loads)
    }

    @Test
    fun `cancelling while loading leaves the sink loading and retries on the next run`() = runTest {
        val scope = FakeScreenStateScope()
        val libs = libsOf("org.example:library")
        var loads = 0

        val collector = launch {
            scope.settingsScreenStateProducer(
                loadLibraries = {
                    loads++
                    awaitCancellation()
                },
            ).first()
        }
        runCurrent()
        collector.cancel()
        runCurrent()
        assertEquals(Loadable.Loading, scope.librariesSink().value)
        assertEquals(1, loads)

        val state = scope.settingsScreenStateProducer(
            loadLibraries = {
                loads++
                libs
            },
        ).first()
        assertEquals(Loadable.Ok(libs), state.libraries)
        assertEquals(2, loads)
    }
}

private fun FakeScreenStateScope.librariesSink() =
    mutablePersistedFlow<Loadable<Libs>>("libraries", Loadable.Loading)

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
