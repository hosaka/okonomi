package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okonomi.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

private const val LIBRARIES_RESOURCE_PATH = "files/aboutlibraries.json"

@Composable
fun produceSettingsScreenState(): State<SettingsState> = produceScreenState(
    key = "settings",
    initial = SettingsState(),
) {
    settingsScreenStateProducer()
}

suspend fun ScreenStateScope.settingsScreenStateProducer(
    loadLibraries: suspend () -> Libs = ::loadBundledLibraries,
): Flow<SettingsState> {
    // Only a successful load is kept between runs of the producer, so
    // coming back to the tab shows the list right away while a failed
    // run is retried the next time the screen is shown.
    val librariesSink = mutablePersistedFlow<Loadable<Libs>>(
        key = "libraries",
        initial = Loadable.Loading,
    )
    return flow {
        if (librariesSink.value is Loadable.Loading) {
            val libraries = loadLibrariesOrNull(loadLibraries)
            if (libraries != null) {
                librariesSink.value = Loadable.Ok(libraries)
            } else {
                emit(SettingsState(libraries = Loadable.Ok(emptyLibs())))
            }
        }
        emitAll(
            librariesSink
                .map { libraries ->
                    SettingsState(libraries = libraries)
                },
        )
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

private suspend fun loadLibrariesOrNull(
    loadLibraries: suspend () -> Libs,
): Libs? = try {
    loadLibraries()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // A missing or corrupt resource must never take the screen down.
    null
}

private fun emptyLibs(): Libs = Libs(
    libraries = emptyList(),
    licenses = emptySet(),
)
