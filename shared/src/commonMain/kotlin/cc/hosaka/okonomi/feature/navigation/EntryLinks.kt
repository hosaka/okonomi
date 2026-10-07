package cc.hosaka.okonomi.feature.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The entry id in an `okonomi://entry/<id>` link — the link every card
 * sent to AnkiDroid carries — or null for anything else.
 *
 * Strict on purpose: the link comes from outside the app, so anything
 * but the scheme, the `entry` host and one positive decimal id (no sign,
 * no further path, no query) is not a link to an entry and is ignored.
 * Whether the dictionary carries that id is not decided here; an id it
 * lacks opens the entry view's own not-found state.
 */
fun parseEntryLink(uri: String): Long? {
    val prefix = "$ENTRY_LINK_SCHEME://$ENTRY_LINK_HOST/"
    if (!uri.startsWith(prefix, ignoreCase = true)) return null
    val id = uri.substring(prefix.length)
    if (id.isEmpty() || !id.all { it in '0'..'9' }) return null
    return id.toLongOrNull()?.takeIf { it > 0 }
}

const val ENTRY_LINK_SCHEME = "okonomi"
const val ENTRY_LINK_HOST = "entry"

/**
 * Entry links handed in by the platform, waiting for the shell to open
 * them. A link that arrives before the shell is composed (a cold start)
 * waits for it. Of several waiting, only the newest is kept (the channel
 * is conflated) — which the reader cannot tell from both being opened in
 * turn, since each link drops Search to its root before pushing: either
 * way the entry left open is the one tapped last.
 */
class EntryLinks {
    private val pending = Channel<Long>(Channel.CONFLATED)

    /** The ids to open, each delivered once, to one collector. */
    val requests: Flow<Long> = pending.receiveAsFlow()

    /** Queues [uri] if it is an entry link; returns whether it was one. */
    fun open(uri: String): Boolean {
        val entryId = parseEntryLink(uri) ?: return false
        pending.trySend(entryId)
        return true
    }
}

/**
 * The app-lifetime queue the Android activity forwards `okonomi://`
 * links into and `HomeScreen` opens them from.
 */
val appEntryLinks: EntryLinks = EntryLinks()
