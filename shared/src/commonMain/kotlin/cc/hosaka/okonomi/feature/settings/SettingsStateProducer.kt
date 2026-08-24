package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo
import cc.hosaka.okonomi.db.loadDictionaryInfo
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

@Composable
fun produceSettingsScreenState(): State<SettingsState> = produceScreenState(
    key = "settings",
    initial = SettingsState(),
) {
    settingsScreenStateProducer()
}

suspend fun ScreenStateScope.settingsScreenStateProducer(
    loadDictionary: suspend () -> DictionaryInfo = ::loadDictionaryInfo,
): Flow<SettingsState> {
    // Only a successful load is kept between runs of the producer, so
    // coming back to the tab shows the content right away while a failed
    // run is retried the next time the screen is shown.
    val dictionarySink = mutablePersistedFlow<Loadable<DictionaryInfo?>>(
        key = "dictionary",
        initial = Loadable.Loading,
    )
    return flow {
        coroutineScope {
            // A failure only surfaces a per-run fallback; the sink stays
            // Loading so the next run retries the load.
            val dictionaryFallback = MutableStateFlow<Loadable<DictionaryInfo?>?>(null)
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
                    dictionarySink,
                    dictionaryFallback,
                ) { dictionary, fallback ->
                    SettingsState(
                        // The sink wins as soon as it holds a value so a late
                        // successful load always replaces a fallback.
                        dictionary = if (dictionary is Loadable.Ok) dictionary else fallback ?: dictionary,
                    )
                },
            )
        }
    }
}

private suspend fun <T : Any> loadOrNull(
    load: suspend () -> T,
): T? = try {
    load()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // A missing dictionary must never take the screen down.
    null
}
