package cc.hosaka.okonomi.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import cc.hosaka.okonomi.db.AndroidAppContext
import cc.hosaka.okonomi.user.printUserDataFailure
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun appAnkiExport(): AnkiExport? = AnkiDroidExport

/**
 * AnkiDroid's instant-add provider, spoken to directly through a
 * [ContentResolver] rather than through its API library: that library
 * is JitPack-only and LGPL-3.0, and a send needs six provider calls.
 * The names below mirror `FlashCardsContract` and `AddContentApi` in
 * Anki-Android (checked against v2.24.1).
 *
 * An object that reaches the context only when used, so constructing
 * the Favourites producer never needs [AndroidAppContext] initialised.
 */
private object AnkiDroidExport : AnkiExport {
    private val context: Context get() = AndroidAppContext.applicationContext

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

    override suspend fun send(notes: List<AnkiNote>): AnkiSendResult = withContext(Dispatchers.IO) {
        sendToAnkiDroid(
            client = ProviderAnkiDroidClient(context.contentResolver),
            notes = notes,
            report = printUserDataFailure,
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
 * into the ones a send has a dialog for. Those arrive across Binder as
 * the only exception types a Parcel carries: `SecurityException` for a
 * missing permission and `IllegalStateException` for "storage is not
 * configured" — a collection AnkiDroid has never been opened to create.
 * A null cursor or URI means the provider could not be reached.
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

    override fun findNoteType(name: String): Long? = query(
        AnkiDroidContract.MODELS,
        arrayOf(AnkiDroidContract.MODEL_ID, AnkiDroidContract.MODEL_NAME),
    ) { cursor ->
        cursor.rows().firstOrNull { it.string(AnkiDroidContract.MODEL_NAME) == name }
            ?.long(AnkiDroidContract.MODEL_ID)
    }

    /**
     * The provider makes a note type with placeholder templates, one per
     * card, and each is then overwritten in place — the way
     * `AddContentApi.addNewCustomModel` does it.
     */
    override fun createNoteType(deckId: Long): Long {
        val values = ContentValues().apply {
            put(AnkiDroidContract.MODEL_NAME, AnkiNoteType.NAME)
            put(AnkiDroidContract.MODEL_FIELD_NAMES, AnkiNoteType.fields.joinToString(ANKI_FIELD_SEPARATOR))
            put(AnkiDroidContract.MODEL_NUM_CARDS, AnkiNoteType.templates.size)
            put(AnkiDroidContract.MODEL_CSS, AnkiNoteType.CSS)
            put(AnkiDroidContract.MODEL_DECK_ID, deckId)
            put(AnkiDroidContract.MODEL_SORT_FIELD_INDEX, 0)
        }
        val modelUri = insert(AnkiDroidContract.MODELS, values)
        val templatesUri = Uri.withAppendedPath(modelUri, "templates")
        AnkiNoteType.templates.forEachIndexed { ord, template ->
            val templateValues = ContentValues().apply {
                put(AnkiDroidContract.TEMPLATE_NAME, template.name)
                put(AnkiDroidContract.TEMPLATE_QUESTION_FORMAT, template.front)
                put(AnkiDroidContract.TEMPLATE_ANSWER_FORMAT, template.back)
            }
            call { resolver.update(Uri.withAppendedPath(templatesUri, ord.toString()), templateValues, null, null) }
        }
        return modelUri.idSegment()
    }

    /**
     * Every note of the note type, compared on field 0 here rather than
     * through the provider's checksum column: the checksum is computed
     * over Anki's HTML stripping, and a copy of that rule that drifted
     * would make every send add everything again. Both sides are
     * compared in NFC, which is what Anki normalises stored fields to.
     */
    override fun existingWords(noteTypeId: Long, words: List<String>): Set<String> {
        val wanted = words.groupBy { it.nfc() }
        return query(
            AnkiDroidContract.NOTES_V2,
            arrayOf(AnkiDroidContract.NOTE_FLDS),
            selection = "${AnkiDroidContract.NOTE_MID}=$noteTypeId",
        ) { cursor ->
            cursor.rows()
                .mapNotNull { row ->
                    row.string(AnkiDroidContract.NOTE_FLDS)?.substringBefore(ANKI_FIELD_SEPARATOR)?.nfc()
                }
                .flatMap { wanted[it].orEmpty() }
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
        throw AnkiDroidException.NoCollection(e)
    }
}

private fun Uri.idSegment(): Long =
    lastPathSegment?.toLongOrNull() ?: error("AnkiDroid answered with no id: $this")

private fun String.nfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)

private fun Cursor.rows(): Sequence<Cursor> = generateSequence { if (moveToNext()) this else null }

private fun Cursor.string(column: String): String? = getString(getColumnIndexOrThrow(column))

private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
