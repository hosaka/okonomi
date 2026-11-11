package cc.hosaka.okonomi.feature.navigation

import android.content.ComponentName
import android.content.Intent

/**
 * The boolean extra on a forwarded lookup saying the text was selected
 * in okonomi itself; see [TextLookup.fromOkonomi].
 */
internal const val EXTRA_FROM_OKONOMI = "cc.hosaka.okonomi.extra.FROM_OKONOMI"

/**
 * The intent `ProcessTextActivity` starts for the selection [source]
 * brought it, or null when there is nothing to start.
 *
 * The selection toolbar starts its target expecting a result, and
 * Android then puts that activity in the caller's task whatever its
 * launch mode. So the trampoline lives there, and this is what moves the
 * lookup out: an intent explicit to [target] (`MainActivity`) with
 * `NEW_TASK`, which does honour `singleTask`, carrying the tidied query
 * (see [selectionQuery]) rather than the raw selection, so a huge one
 * never crosses Binder a second time. [fromOkonomi] travels with it.
 *
 * Null for a blank selection, which opens nothing, and for a replay from
 * Recents: the intent built here is a fresh one, so passing a replay on
 * would drop the flag that `MainActivity`'s own guard looks for.
 */
fun processTextForward(source: Intent, target: ComponentName, fromOkonomi: Boolean): Intent? {
    if (source.isRecentsReplay()) return null
    val query = source.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let(::selectionQuery) ?: return null
    return Intent(Intent.ACTION_PROCESS_TEXT)
        .setComponent(target)
        .putExtra(Intent.EXTRA_PROCESS_TEXT, query)
        .putExtra(EXTRA_FROM_OKONOMI, fromOkonomi)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * What `MainActivity` does with an intent it is started or re-entered
 * with: an `okonomi://entry/<id>` link goes to [links], a lookup
 * forwarded by `ProcessTextActivity` to [lookups] with its from-okonomi
 * flag, and anything else is ignored.
 *
 * Nothing at all when the task was brought back from Recents: Android
 * then replays the intent that first started it, and a link tapped or a
 * word looked up long ago would open again over whatever the reader has
 * moved on to.
 *
 * A lookup is also ignored when [startedForResult]. `MainActivity` has no
 * `PROCESS_TEXT` filter, but it is exported, so a caller can still name
 * it and start it for a result, and Android then places a second copy in
 * the caller's task. The queue is shared by every copy, so the lookup
 * could open in the hidden one while the reader faces an empty Search,
 * as happened on a device before the trampoline existed.
 */
fun handOffToShell(
    intent: Intent?,
    startedForResult: Boolean,
    links: EntryLinks = appEntryLinks,
    lookups: TextLookups = appTextLookups,
) {
    if (intent == null || intent.isRecentsReplay()) return
    when (intent.action) {
        Intent.ACTION_VIEW -> intent.data?.let { uri -> links.open(uri.toString()) }

        Intent.ACTION_PROCESS_TEXT -> {
            if (startedForResult) return
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) ?: return
            lookups.lookUp(text, fromOkonomi = intent.getBooleanExtra(EXTRA_FROM_OKONOMI, false))
        }
    }
}

private fun Intent.isRecentsReplay(): Boolean = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
