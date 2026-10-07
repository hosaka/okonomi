package cc.hosaka.okonomi.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import cc.hosaka.okonomi.db.AndroidAppContext
import cc.hosaka.okonomi.user.printUserDataFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun appAnkiExport(): AnkiExport? = appAnkiDroidExport

/**
 * Reaches the context only when used, so constructing the Favourites
 * producer never needs [AndroidAppContext] initialised.
 */
private val appAnkiDroidExport = AnkiDroidExport { AndroidAppContext.applicationContext }

/**
 * AnkiDroid's instant-add provider, spoken to directly through a
 * [ContentResolver] rather than through its API library: that library
 * is JitPack-only and LGPL-3.0, and a send needs six provider calls.
 * The names below mirror `FlashCardsContract` and `AddContentApi` in
 * Anki-Android (checked against v2.24.1).
 */
internal class AnkiDroidExport(private val contextProvider: () -> Context) : AnkiExport {
    private val context: Context get() = contextProvider()

    override fun access(): AnkiAccess = when {
        // Unresolvable both when AnkiDroid is missing and when its
        // "Enable AnkiDroid API" setting is off, which disables the
        // provider component. Android 11+ needs the <queries> entry in
        // this module's manifest to see it at all.
        context.packageManager.resolveContentProvider(AnkiDroidContract.AUTHORITY, 0) == null ->
            AnkiAccess.Unavailable

        context.checkSelfPermission(ANKIDROID_PERMISSION) != PackageManager.PERMISSION_GRANTED ->
            AnkiAccess.NeedsPermission

        else -> AnkiAccess.Granted
    }

    override suspend fun send(notes: List<AnkiNote>, linkLabel: String): AnkiSendResult =
        withContext(Dispatchers.IO) {
            sendToAnkiDroid(
                client = ProviderAnkiDroidClient(context.contentResolver),
                notes = notes,
                report = printUserDataFailure,
                linkLabel = linkLabel,
            )
        }
}

/** AnkiDroid's runtime permission, which a send asks for on first use. */
const val ANKIDROID_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

private object AnkiDroidContract {
    const val AUTHORITY = "com.ichi2.anki.flashcards"
    private val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

    /** Notes through the Anki search syntax; also the insert endpoint. */
    val NOTES: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")

    /** Notes through direct SQL over the notes table. */
    val NOTES_V2: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes_v2")
    val MODELS: Uri = Uri.withAppendedPath(AUTHORITY_URI, "models")
    val DECKS: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")

    const val NOTE_MID = "mid"
    const val NOTE_FLDS = "flds"
    const val NOTE_DECK_ID_QUERY_PARAM = "deckId"

    const val MODEL_ID = "_id"
    const val MODEL_NAME = "name"
    const val MODEL_FIELD_NAMES = "field_names"
    const val MODEL_NUM_CARDS = "num_cards"
    const val MODEL_CSS = "css"
    const val MODEL_DECK_ID = "deck_id"
    const val MODEL_SORT_FIELD_INDEX = "sort_field_index"

    const val TEMPLATE_NAME = "card_template_name"
    const val TEMPLATE_QUESTION_FORMAT = "question_format"
    const val TEMPLATE_ANSWER_FORMAT = "answer_format"

    const val DECK_ID = "deck_id"
    const val DECK_NAME = "deck_name"
}

/**
 * Every call goes through [call], which turns the provider's failures
 * into the ones a send has a dialog for: `SecurityException` for a
 * missing permission, and the `IllegalStateException` AnkiDroid throws
 * with "storage is not configured" for a collection it has never been
 * opened to create. Any other `IllegalStateException` is an ordinary
 * failure.
 *
 * Across Binder a Parcel carries only a fixed handful of exception types
 * (among them `SecurityException`, `IllegalArgumentException`,
 * `IllegalStateException`, `NullPointerException` and
 * `UnsupportedOperationException`), with their messages. Anything else
 * the provider throws does not arrive as itself: the call fails on
 * AnkiDroid's side and reaches us as a null cursor or URI, or a zero
 * count — which is why a null cursor reads as unreachable and a short
 * insert is never taken for success.
 */
internal class ProviderAnkiDroidClient(private val resolver: ContentResolver) : AnkiDroidClient {

    override fun findDeck(name: String): Long? = query(
        AnkiDroidContract.DECKS,
        arrayOf(AnkiDroidContract.DECK_ID, AnkiDroidContract.DECK_NAME),
    ) { cursor ->
        // Anki's deck names are unique ignoring case: creating
        // "Okonomi Favourites" beside "okonomi favourites" throws.
        cursor.rows().firstOrNull { it.string(AnkiDroidContract.DECK_NAME).equals(name, ignoreCase = true) }
            ?.long(AnkiDroidContract.DECK_ID)
    }

    override fun createDeck(name: String): Long {
        val values = ContentValues().apply { put(AnkiDroidContract.DECK_NAME, name) }
        return insert(AnkiDroidContract.DECKS, values).idSegment()
    }

    override fun findNoteType(name: String): FoundNoteType? {
        val match = query(
            AnkiDroidContract.MODELS,
            arrayOf(AnkiDroidContract.MODEL_ID, AnkiDroidContract.MODEL_NAME, AnkiDroidContract.MODEL_FIELD_NAMES),
        ) { cursor ->
            cursor.rows().firstOrNull { it.string(AnkiDroidContract.MODEL_NAME) == name }?.let { row ->
                row.long(AnkiDroidContract.MODEL_ID) to
                    row.string(AnkiDroidContract.MODEL_FIELD_NAMES).orEmpty().split(ANKI_FIELD_SEPARATOR)
            }
        } ?: return null
        val (id, fields) = match
        val templateNames = query(
            Uri.withAppendedPath(Uri.withAppendedPath(AnkiDroidContract.MODELS, id.toString()), "templates"),
            arrayOf(AnkiDroidContract.TEMPLATE_NAME),
        ) { cursor -> cursor.rows().mapNotNull { it.string(AnkiDroidContract.TEMPLATE_NAME) }.toList() }
        return FoundNoteType(id = id, fields = fields, templateNames = templateNames)
    }

    /**
     * The provider makes a note type with placeholder templates, one per
     * card, and each is then overwritten in place — the way
     * `AddContentApi.addNewCustomModel` does it.
     */
    override fun createNoteType(deckId: Long, templates: List<AnkiCardTemplate>): Long {
        val values = ContentValues().apply {
            put(AnkiDroidContract.MODEL_NAME, AnkiNoteType.NAME)
            put(AnkiDroidContract.MODEL_FIELD_NAMES, AnkiNoteType.fields.joinToString(ANKI_FIELD_SEPARATOR))
            put(AnkiDroidContract.MODEL_NUM_CARDS, templates.size)
            put(AnkiDroidContract.MODEL_CSS, AnkiNoteType.CSS)
            put(AnkiDroidContract.MODEL_DECK_ID, deckId)
            put(AnkiDroidContract.MODEL_SORT_FIELD_INDEX, 0)
        }
        val modelUri = insert(AnkiDroidContract.MODELS, values)
        val templatesUri = Uri.withAppendedPath(modelUri, "templates")
        templates.forEachIndexed { ord, template ->
            val templateValues = ContentValues().apply {
                put(AnkiDroidContract.TEMPLATE_NAME, template.name)
                put(AnkiDroidContract.TEMPLATE_QUESTION_FORMAT, template.front)
                put(AnkiDroidContract.TEMPLATE_ANSWER_FORMAT, template.back)
            }
            val updated = call {
                resolver.update(Uri.withAppendedPath(templatesUri, ord.toString()), templateValues, null, null)
            }
            check(updated >= 1) { "AnkiDroid did not write card template $ord of note type $modelUri" }
        }
        return modelUri.idSegment()
    }

    /**
     * Every note of the note type, read through notes_v2 (direct SQL on
     * the notes table) and compared on the EntryId field here. The
     * provider's own duplicate lookup only knows field 0, through a
     * checksum, and field 0 is the Word — which two entries can share.
     */
    override fun existingEntryIds(noteTypeId: Long, entryIds: List<Long>): Set<Long> {
        val wanted = entryIds.toSet()
        val entryIdField = AnkiNoteType.fields.indexOf("EntryId")
        return query(
            AnkiDroidContract.NOTES_V2,
            arrayOf(AnkiDroidContract.NOTE_FLDS),
            selection = "${AnkiDroidContract.NOTE_MID}=$noteTypeId",
        ) { cursor ->
            cursor.rows()
                .mapNotNull { row ->
                    row.string(AnkiDroidContract.NOTE_FLDS)
                        ?.split(ANKI_FIELD_SEPARATOR)
                        ?.getOrNull(entryIdField)
                        ?.trim()
                        ?.toLongOrNull()
                }
                .filter { it in wanted }
                .toSet()
        }
    }

    override fun addNotes(noteTypeId: Long, deckId: Long, notes: List<AnkiNote>): Int {
        val uri = AnkiDroidContract.NOTES.buildUpon()
            .appendQueryParameter(AnkiDroidContract.NOTE_DECK_ID_QUERY_PARAM, deckId.toString())
            .build()
        val values = notes.map { note ->
            ContentValues().apply {
                put(AnkiDroidContract.NOTE_MID, noteTypeId)
                put(AnkiDroidContract.NOTE_FLDS, note.fields.joinToString(ANKI_FIELD_SEPARATOR))
            }
        }.toTypedArray()
        return call { resolver.bulkInsert(uri, values) }
    }

    private fun <T> query(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        read: (Cursor) -> T,
    ): T {
        val cursor = call { resolver.query(uri, projection, selection, null, null) }
            ?: throw AnkiDroidException.Unavailable()
        return cursor.use(read)
    }

    private fun insert(uri: Uri, values: ContentValues): Uri =
        call { resolver.insert(uri, values) } ?: error("AnkiDroid created nothing at $uri")

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw AnkiDroidException.PermissionMissing(e)
    } catch (e: IllegalStateException) {
        if (e.message.orEmpty().contains(STORAGE_NOT_CONFIGURED, ignoreCase = true)) {
            throw AnkiDroidException.NoCollection(e)
        }
        throw e
    }
}

/** AnkiDroid's message for a collection that does not exist yet (CardContentProvider.getColUnsafe). */
private const val STORAGE_NOT_CONFIGURED = "storage is not configured"

private fun Uri.idSegment(): Long =
    lastPathSegment?.toLongOrNull() ?: error("AnkiDroid answered with no id: $this")

private fun Cursor.rows(): Sequence<Cursor> = generateSequence { if (moveToNext()) this else null }

private fun Cursor.string(column: String): String? = getString(getColumnIndexOrThrow(column))

private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
