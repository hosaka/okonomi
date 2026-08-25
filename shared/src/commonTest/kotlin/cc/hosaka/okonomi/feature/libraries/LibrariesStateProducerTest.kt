package cc.hosaka.okonomi.feature.libraries

import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateViewModel
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Developer
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.entity.Organization
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LibrariesStateProducerTest {
    private val dispatcher = StandardTestDispatcher()

    private suspend fun FakeScreenStateScope.producer(
        loadLibraries: suspend () -> Libs = { libsOf("org.example:library") },
    ): Flow<LibrariesState> = librariesScreenStateProducer(loadLibraries)

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
            initial = LibrariesState(),
        ) {
            librariesScreenStateProducer(
                loadLibraries = { load.await() },
            )
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
    fun `a failing loader yields a load failure and is retried on the next run`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        val loadLibraries: suspend () -> Libs = {
            loads++
            error("no resource")
        }

        val state = scope.producer(loadLibraries = loadLibraries)
            .first { it.libraries !is Loadable.Loading }
        assertEquals(Loadable.Ok(null), state.libraries)
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
    fun `an empty library list is a successful load and is kept`() = runTest {
        val scope = FakeScreenStateScope()
        val empty = Libs(libraries = emptyList(), licenses = emptySet())

        val state = scope.producer(loadLibraries = { empty })
            .first { it.libraries !is Loadable.Loading }
        assertEquals(Loadable.Ok(empty), state.libraries)
        assertEquals(Loadable.Ok<Libs?>(empty), scope.librariesSink().value)
    }
}

private fun FakeScreenStateScope.librariesSink() =
    mutablePersistedFlow<Loadable<Libs?>>("libraries", Loadable.Loading)

/**
 * A [Libs] carrying one library per id. Internal rather than private:
 * the screen's own test composes the credits list from the same fixture,
 * so the two cannot drift apart in what they think a library looks like.
 */
internal fun libsOf(
    vararg uniqueIds: String,
    website: String? = null,
    licenses: Set<License> = emptySet(),
): Libs = Libs(
    libraries = uniqueIds.map { uniqueId ->
        Library(
            uniqueId = uniqueId,
            artifactVersion = "1.0.0",
            name = uniqueId.substringAfter(':'),
            description = null,
            website = website,
            developers = emptyList(),
            organization = null,
            scm = null,
            licenses = licenses,
        )
    },
    licenses = licenses,
)
