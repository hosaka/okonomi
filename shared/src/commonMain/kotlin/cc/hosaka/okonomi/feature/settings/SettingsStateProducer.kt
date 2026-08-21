package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo
import cc.hosaka.okonomi.db.loadDictionaryInfo
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
fun produceSettingsScreenState(): State<SettingsState> = produceScreenState(
    key = "settings",
    initial = SettingsState(),
) {
    settingsScreenStateProducer()
}

suspend fun ScreenStateScope.settingsScreenStateProducer(
    loadLibraries: suspend () -> Libs = ::loadBundledLibraries,
    loadDictionary: suspend () -> DictionaryInfo = ::loadDictionaryInfo,
): Flow<SettingsState> {
    // Only a successful load is kept between runs of the producer, so
    // coming back to the tab shows the content right away while a failed
    // run is retried the next time the screen is shown.
    val librariesSink = mutablePersistedFlow<Loadable<Libs>>(
        key = "libraries",
        initial = Loadable.Loading,
    )
    val dictionarySink = mutablePersistedFlow<Loadable<DictionaryInfo?>>(
        key = "dictionary",
        initial = Loadable.Loading,
    )
    return flow {
        coroutineScope {
            // The loads run concurrently and states emit progressively:
            // the libraries list must never wait behind the dictionary
            // load, whose first run copies the whole bundled database.
            // A failure only surfaces a per-run fallback; the sink stays
            // Loading so the next run retries the load.
            val librariesFallback = MutableStateFlow<Loadable<Libs>?>(null)
            val dictionaryFallback = MutableStateFlow<Loadable<DictionaryInfo?>?>(null)
            if (librariesSink.value is Loadable.Loading) {
                launch {
                    val libraries = loadOrNull(loadLibraries)
                    if (libraries != null) {
                        librariesSink.value = Loadable.Ok(libraries)
                    } else {
                        librariesFallback.value = Loadable.Ok(emptyLibs())
                    }
                }
            }
            if (dictionarySink.value is Loadable.Loading) {
                launch {
                    val dictionary = loadOrNull(loadDictionary)
                    if (dictionary != null) {
                        dictionarySink.value = Loadable.Ok(dictionary)
                    } else {
                        dictionaryFallback.value = Loadable.Ok(null)
                    }
                }
            }
            emitAll(
                combine(
                    librariesSink,
                    librariesFallback,
                    dictionarySink,
                    dictionaryFallback,
                ) { libraries, libFallback, dictionary, dictFallback ->
                    // The sink wins as soon as it holds a value so a late
                    // successful load always replaces a fallback.
                    SettingsState(
                        libraries = if (libraries is Loadable.Ok) libraries else libFallback ?: libraries,
                        dictionary = if (dictionary is Loadable.Ok) dictionary else dictFallback ?: dictionary,
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

private fun emptyLibs(): Libs = Libs(
    libraries = emptyList(),
    licenses = emptySet(),
)
