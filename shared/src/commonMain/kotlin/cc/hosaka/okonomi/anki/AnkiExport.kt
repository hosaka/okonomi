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
     * Adds every note in [notes] whose Word is not already in AnkiDroid.
     * Never throws: every outcome, failures included, is a result the
     * reader is told about.
     */
    suspend fun send(notes: List<AnkiNote>): AnkiSendResult
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

    data object PermissionDenied : AnkiSendResult

    /** AnkiDroid has never been opened, so it has no collection to add to. */
    data object NoCollection : AnkiSendResult

    /**
     * Anything else. [added] of the [attempted] new notes landed before
     * it failed; zero means nothing was sent.
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
    const val NAME = "cc.hosaka.okonomi"
    val fields = listOf("Word", "Reading", "Meaning")

    /** A forward card (Word → Reading and Meaning) and its reverse. */
    val templates = listOf(
        AnkiCardTemplate(
            name = "Forward",
            front = "{{Word}}",
            back = "{{FrontSide}}<hr id=answer>{{Reading}}<br><br>{{Meaning}}",
        ),
        AnkiCardTemplate(
            name = "Reverse",
            front = "{{Reading}}<br><br>{{Meaning}}",
            back = "{{FrontSide}}<hr id=answer>{{Word}}",
        ),
    )

    const val CSS = ".card { font-family: sans-serif; font-size: 22px; text-align: center; " +
        "color: black; background-color: white; }"
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

    /** The id of the note type called [name] exactly, or null. */
    fun findNoteType(name: String): Long?

    fun createNoteType(deckId: Long): Long

    /** The subset of [words] already a Word (field 0) of a note of [noteTypeId]. */
    fun existingWords(noteTypeId: Long, words: List<String>): Set<String>

    /** Inserts [notes] in one transaction and returns how many landed. */
    fun addNotes(noteTypeId: Long, deckId: Long, notes: List<AnkiNote>): Int
}

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
 * Once anything has landed, a failure is reported as how much landed,
 * whatever its cause: by then the reader needs the count more than the
 * reason.
 */
fun sendToAnkiDroid(
    client: AnkiDroidClient,
    notes: List<AnkiNote>,
    report: UserDataFailureReporter,
    batchSize: Int = ANKI_BATCH_SIZE,
): AnkiSendResult {
    require(batchSize > 0) { "batchSize must be positive: $batchSize" }
    var added = 0
    var attempted = 0
    return try {
        val deckId = client.findDeck(ANKI_DECK_NAME) ?: client.createDeck(ANKI_DECK_NAME)
        val noteTypeId = client.findNoteType(AnkiNoteType.NAME) ?: client.createNoteType(deckId)
        val present = client.existingWords(noteTypeId, notes.map { it.word }.distinct())
        val fresh = notes.filterNot { it.word in present }
        if (fresh.isEmpty()) return AnkiSendResult.NothingNew
        attempted = fresh.size
        fresh.chunked(batchSize).forEach { batch ->
            added += client.addNotes(noteTypeId, deckId, batch)
        }
        AnkiSendResult.Sent(added = added, alreadyThere = notes.size - fresh.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        when {
            added > 0 -> {
                report("a send to AnkiDroid stopped after $added of $attempted notes", e)
                AnkiSendResult.Failed(added = added, attempted = attempted)
            }

            e is AnkiDroidException.Unavailable -> AnkiSendResult.Unavailable
            e is AnkiDroidException.PermissionMissing -> AnkiSendResult.PermissionDenied
            e is AnkiDroidException.NoCollection -> AnkiSendResult.NoCollection
            else -> {
                report("the saved words could not be sent to AnkiDroid", e)
                AnkiSendResult.Failed(added = 0, attempted = attempted)
            }
        }
    }
}
