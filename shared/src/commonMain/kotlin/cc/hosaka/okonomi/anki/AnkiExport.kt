package cc.hosaka.okonomi.anki

import cc.hosaka.okonomi.user.UserDataFailureReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Where Favourites go to become flashcards. Only Android has one —
 * AnkiDroid's instant-add provider — so [appAnkiExport] is null on iOS,
 * and a null is how the Favourites menu knows to offer nothing.
 */
interface AnkiExport {
    /**
     * Whether a send could run right now. Cheap and synchronous: it asks
     * the platform, not AnkiDroid.
     */
    fun access(): AnkiAccess

    /**
     * Adds every note in [notes] whose entry is not already in AnkiDroid.
     * [linkLabel] is the text of the cards' link back into okonomi,
     * used only if the note type has to be created. Never throws: every
     * outcome, failures included, is a result the reader is told about.
     */
    suspend fun send(notes: List<AnkiNote>, linkLabel: String): AnkiSendResult
}

enum class AnkiAccess {
    /** Not installed, or installed with its API switched off. */
    Unavailable,

    /** Installed, but the reader has not granted okonomi access to it yet. */
    NeedsPermission,
    Granted,
}

/** Everything a send can end in; each has its own dialog. */
sealed interface AnkiSendResult {
    /** [added] notes landed; [alreadyThere] were skipped as already in AnkiDroid. */
    data class Sent(val added: Int, val alreadyThere: Int) : AnkiSendResult

    /** Every word is already in AnkiDroid. */
    data object NothingNew : AnkiSendResult

    /** Not installed, or its API is switched off. */
    data object Unavailable : AnkiSendResult

    /**
     * The reader refused AnkiDroid access. [permanently] when Android will
     * not ask again, so only the app's settings can grant it now.
     */
    data class PermissionDenied(val permanently: Boolean = false) : AnkiSendResult

    /** AnkiDroid has never been opened, so it has no collection to add to. */
    data object NoCollection : AnkiSendResult

    /**
     * Anything else. [attempted] is how many new notes went to be
     * inserted and [added] how many of them landed; both are zero when
     * the send failed before any insert.
     */
    data class Failed(val added: Int, val attempted: Int) : AnkiSendResult
}

/** The platform's way into AnkiDroid, or null where there is none (iOS). */
expect fun appAnkiExport(): AnkiExport?

/**
 * Where a send runs. Not the screen's: the reader can leave the tab the
 * moment they tap, and a send stopped halfway would leave some words in
 * AnkiDroid and no dialog saying how many.
 */
internal val ankiSendScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** The deck the words land in, found by name and created when missing. */
const val ANKI_DECK_NAME = "Okonomi Favourites"

/**
 * At most this many notes per insert. Every insert is one Binder
 * transaction, and those fail outright past about 1 MB; a note is a few
 * hundred bytes, so this stays far below that.
 */
internal const val ANKI_BATCH_SIZE = 200

/** The note type okonomi's cards use, created in AnkiDroid when missing. */
object AnkiNoteType {
    const val NAME = "Okonomi"
    val fields = listOf("Word", "Reading", "Meaning", "EntryId")

    /**
     * A forward card (Word → Reading and Meaning) and its reverse. Both
     * backs end in a link that opens the entry in okonomi
     * (`okonomi://entry/<id>`), labelled [linkLabel]. The link is built
     * here rather than stored in a field, so EntryId is never shown as
     * text; it is never on a front, where it would give the answer away.
     */
    fun templates(linkLabel: String): List<AnkiCardTemplate> {
        val link = "<br><br><a href=\"okonomi://entry/{{EntryId}}\">${ankiFieldHtml(linkLabel)}</a>"
        // lang="ja" so the Japanese is drawn with Japanese glyph forms;
        // without it a device whose fallback font is Chinese draws 直 or
        // 骨 the Chinese way.
        val word = "<span lang=\"ja\">{{Word}}</span>"
        val reading = "<span lang=\"ja\">{{Reading}}</span>"
        return listOf(
            AnkiCardTemplate(
                name = "Forward",
                front = word,
                back = "{{FrontSide}}<hr id=answer>$reading<br><br>{{Meaning}}$link",
            ),
            AnkiCardTemplate(
                name = "Reverse",
                front = "$reading<br><br>{{Meaning}}",
                back = "{{FrontSide}}<hr id=answer>$word$link",
            ),
        )
    }

    /** The template names, which tell a finished note type from a half-built one. */
    val templateNames: List<String> get() = templates("").map { it.name }

    /**
     * Layout only. Colours are left to Anki's own, which follow its night
     * mode; a fixed black on white would stay glaring in the dark.
     */
    const val CSS = ".card { font-family: sans-serif; font-size: 22px; text-align: center; }"
}

data class AnkiCardTemplate(val name: String, val front: String, val back: String)

/**
 * The operations a send needs from AnkiDroid, one per provider call, so
 * that what a send does can run against a fake. Blocking: callers run it
 * off the main thread.
 *
 * Every method throws an [AnkiDroidException] for the failures a send
 * has a dialog for, and anything else for the rest.
 */
interface AnkiDroidClient {
    /** The id of the deck called [name], compared as Anki does: ignoring case. */
    fun findDeck(name: String): Long?

    fun createDeck(name: String): Long

    /** The note type called [name] exactly, with its field and template names, or null. */
    fun findNoteType(name: String): FoundNoteType?

    /**
     * Creates [AnkiNoteType] with [templates] and returns its id. Throws
     * if any template could not be written, rather than leave a note type
     * with placeholder cards behind as if it were finished.
     */
    fun createNoteType(deckId: Long, templates: List<AnkiCardTemplate>): Long

    /** The subset of [entryIds] already the EntryId (field 3) of a note of [noteTypeId]. */
    fun existingEntryIds(noteTypeId: Long, entryIds: List<Long>): Set<Long>

    /**
     * Inserts [notes] and returns how many landed — which can be fewer,
     * down to zero when AnkiDroid's process died under the call.
     */
    fun addNotes(noteTypeId: Long, deckId: Long, notes: List<AnkiNote>): Int
}

data class FoundNoteType(val id: Long, val fields: List<String>, val templateNames: List<String>)

/** The failures a send has a dialog of its own for. */
sealed class AnkiDroidException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unavailable(cause: Throwable? = null) :
        AnkiDroidException("AnkiDroid's provider could not be reached", cause)

    class PermissionMissing(cause: Throwable? = null) :
        AnkiDroidException("AnkiDroid refused access", cause)

    class NoCollection(cause: Throwable? = null) :
        AnkiDroidException("AnkiDroid has no collection yet", cause)
}

/**
 * The whole of a send, against any [client].
 *
 * The deck and the note type are looked up by name every time and
 * created only when missing: no id is kept, so a deck or note type the
 * reader deleted in AnkiDroid is simply made again. Words already there
 * are skipped, and nothing already in AnkiDroid is ever changed or
 * deleted — a word taken out of Favourites stays in the deck.
 *
 * A note type of our name is used only if its fields and templates are
 * exactly ours, in order: EntryId is read by position, and a note type
 * an earlier build made, or one whose creation failed halfway, would
 * otherwise be written into wrongly or reused with placeholder cards.
 * Anything else is a plain failure — there is no compatibility handling.
 *
 * Notes are sent once per entry. A batch that lands short ends the send
 * there, reported as how many landed: the provider does not say which
 * notes it dropped. Once anything has landed, every failure is reported
 * that way, whatever its cause: by then the reader needs the count more
 * than the reason.
 */
fun sendToAnkiDroid(
    client: AnkiDroidClient,
    notes: List<AnkiNote>,
    report: UserDataFailureReporter,
    linkLabel: String,
    batchSize: Int = ANKI_BATCH_SIZE,
): AnkiSendResult {
    require(batchSize > 0) { "batchSize must be positive: $batchSize" }
    var added = 0
    var attempted = 0
    return try {
        val unique = notes.distinctBy { it.entryId }
        val deckId = client.findDeck(ANKI_DECK_NAME) ?: client.createDeck(ANKI_DECK_NAME)
        val found = client.findNoteType(AnkiNoteType.NAME)
        if (found != null && (found.fields != AnkiNoteType.fields || found.templateNames != AnkiNoteType.templateNames)) {
            error("AnkiDroid's \"${AnkiNoteType.NAME}\" note type is not the one okonomi makes: $found")
        }
        val noteTypeId = found?.id ?: client.createNoteType(deckId, AnkiNoteType.templates(linkLabel))
        val present = client.existingEntryIds(noteTypeId, unique.map { it.entryId })
        val fresh = unique.filterNot { it.entryId in present }
        if (fresh.isEmpty()) return AnkiSendResult.NothingNew
        attempted = fresh.size
        for (batch in fresh.chunked(batchSize)) {
            val landed = client.addNotes(noteTypeId, deckId, batch)
            added += landed.coerceIn(0, batch.size)
            if (landed < batch.size) {
                report("AnkiDroid took $landed of a batch of ${batch.size}; $added of $attempted landed", null)
                return AnkiSendResult.Failed(added = added, attempted = attempted)
            }
        }
        AnkiSendResult.Sent(added = added, alreadyThere = unique.size - fresh.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        when {
            added > 0 -> {
                report("a send to AnkiDroid stopped after $added of $attempted notes", e)
                AnkiSendResult.Failed(added = added, attempted = attempted)
            }

            e is AnkiDroidException.Unavailable -> AnkiSendResult.Unavailable
            e is AnkiDroidException.PermissionMissing -> AnkiSendResult.PermissionDenied()
            e is AnkiDroidException.NoCollection -> AnkiSendResult.NoCollection
            else -> {
                report("the saved words could not be sent to AnkiDroid", e)
                AnkiSendResult.Failed(added = 0, attempted = attempted)
            }
        }
    }
}
