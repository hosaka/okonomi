package cc.hosaka.okonomi.feature.libraries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okonomi.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

private const val LIBRARIES_RESOURCE_PATH = "files/aboutlibraries.json"

@Composable
fun produceLibrariesScreenState(): State<LibrariesState> = produceScreenState(
    key = "libraries",
    initial = LibrariesState(),
) {
    librariesScreenStateProducer()
}

suspend fun ScreenStateScope.librariesScreenStateProducer(
    loadLibraries: suspend () -> Libs = ::loadBundledLibraries,
): Flow<LibrariesState> {
    // Only a successful load is kept between runs of the producer, so
    // coming back to the screen shows the list right away while a failed
    // run is retried the next time the screen is shown.
    val librariesSink = mutablePersistedFlow<Loadable<Libs?>>(
        key = "libraries",
        initial = Loadable.Loading,
    )
    return flow {
        coroutineScope {
            // A failure only surfaces a per-run fallback; the sink stays
            // Loading so the next run retries the load.
            val librariesFallback = MutableStateFlow<Loadable<Libs?>?>(null)
            if (librariesSink.value is Loadable.Loading) {
                launch {
                    val libraries = loadOrNull(loadLibraries)
                    if (libraries != null) {
                        librariesSink.value = Loadable.Ok(libraries)
                    } else {
                        librariesFallback.value = Loadable.Ok(null)
                    }
                }
            }
            emitAll(
                combine(
                    librariesSink,
                    librariesFallback,
                ) { libraries, fallback ->
                    LibrariesState(
                        // The sink wins as soon as it holds a value so a late
                        // successful load always replaces a fallback.
                        libraries = if (libraries is Loadable.Ok) libraries else fallback ?: libraries,
                    )
                },
            )
        }
    }
}

/**
 * Reads the library list exported by the AboutLibraries Gradle plugin
 * from the bundled compose resource. Regenerate it with
 * `./gradlew :shared:exportLibraryDefinitions` after dependencies change.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadBundledLibraries(): Libs = withContext(Dispatchers.Default) {
    val json = Res.readBytes(LIBRARIES_RESOURCE_PATH).decodeToString()
    Libs.Builder()
        .withJson(json)
        .build()
}

private suspend fun <T : Any> loadOrNull(
    load: suspend () -> T,
): T? = try {
    load()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // A missing or corrupt resource must never take the screen down.
    null
}
