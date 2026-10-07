package cc.hosaka.okonomi.anki

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The provider client against a stand-in for AnkiDroid's
 * `CardContentProvider`, registered under its real authority: the URIs,
 * column names and field joining the client sends, and the exceptions it
 * turns into dialogs, are checked against what that provider accepts and
 * throws (Anki-Android v2.24.1), not against the client's own constants.
 *
 * What this cannot show is that the real AnkiDroid agrees; that is the
 * spec's on-device check.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ProviderAnkiDroidClientTest {

    private lateinit var anki: StandInAnkiDroid
    private lateinit var client: ProviderAnkiDroidClient
    private val reports = mutableListOf<String>()

    @Before
    fun setUp() {
        anki = Robolectric.setupContentProvider(StandInAnkiDroid::class.java, "com.ichi2.anki.flashcards")
        val context = ApplicationProvider.getApplicationContext<Context>()
        client = ProviderAnkiDroidClient(context.contentResolver)
    }

    private fun send(notes: List<AnkiNote>) = sendToAnkiDroid(client, notes, { message, _ -> reports += message })

    private fun note(word: String) = AnkiNote(word = word, reading = "よみ", meaning = "meaning")

    @Test
    fun `a first send builds the deck and both templates and inserts into that deck`() {
        val result = send(listOf(note("食べる"), note("飲む")))

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), result, reports.toString())
        val deckId = anki.decks.entries.single { it.value == ANKI_DECK_NAME }.key
        val model = anki.models.values.single()
        assertEquals(AnkiNoteType.NAME, model.name)
        assertEquals(listOf("Word", "Reading", "Meaning"), model.fields)
        assertEquals(deckId, model.deckId)
        assertEquals(AnkiNoteType.templates.map { Triple(it.name, it.front, it.back) }, model.templates)
        assertEquals(listOf(deckId, deckId), anki.notes.map { it.deckId })
        assertEquals(listOf("食べる", "よみ", "meaning"), anki.notes.first().fields)
    }

    @Test
    fun `a re-send finds what is there and adds only the rest`() {
        send(listOf(note("食べる")))

        val result = send(listOf(note("食べる"), note("飲む")))

        assertEquals(AnkiSendResult.Sent(added = 1, alreadyThere = 1), result)
        assertEquals(listOf("食べる", "飲む"), anki.notes.map { it.fields.first() })
        assertEquals(1, anki.decks.values.count { it == ANKI_DECK_NAME })
        assertEquals(1, anki.models.size)
    }

    @Test
    fun `a word stored in another normal form is still the same word`() {
        // U+F91D is a CJK compatibility ideograph that NFC maps to
        // U+6B04, and Anki stores fields in NFC.
        send(listOf(note("\u6B04")))

        val result = send(listOf(note("\uF91D")))

        assertEquals(AnkiSendResult.NothingNew, result)
    }

    @Test
    fun `a word stored un-normalised is still matched by its normal form`() {
        // The other direction: a note stored before Anki normalised it,
        // or by a client that did not, is compared in NFC too.
        anki.decks[2L] = ANKI_DECK_NAME
        send(listOf(note("飲む")))
        val modelId = anki.models.keys.single()
        anki.notes += StandInAnkiDroid.StoredNote(modelId, 2L, listOf("\uF91D", "", ""))

        val result = send(listOf(note("\u6B04")))

        assertEquals(AnkiSendResult.NothingNew, result)
    }

    @Test
    fun `notes of another note type are not mistaken for ours`() {
        anki.notes += StandInAnkiDroid.StoredNote(modelId = 999L, deckId = 1L, fields = listOf("食べる", "", ""))

        val result = send(listOf(note("食べる")))

        assertEquals(AnkiSendResult.Sent(added = 1, alreadyThere = 0), result)
    }

    @Test
    fun `a refused permission becomes the permission dialog`() {
        anki.failure = SecurityException("Permission not granted for: query")

        assertEquals(AnkiSendResult.PermissionDenied, send(listOf(note("食べる"))))
    }

    @Test
    fun `storage not configured becomes the open AnkiDroid first dialog`() {
        anki.failure = IllegalStateException("AnkiDroid storage is not configured")

        assertEquals(AnkiSendResult.NoCollection, send(listOf(note("食べる"))))
    }

    @Test
    fun `any other provider failure is a reported failure`() {
        anki.failure = IllegalArgumentException("uri is not supported")

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), send(listOf(note("食べる"))))
        assertTrue(reports.isNotEmpty())
    }
}

/**
 * Just enough of AnkiDroid's `CardContentProvider` for a send, with its
 * argument checks: a deck name that exists throws, a note type is made
 * with placeholder templates that are then updated by ord, a field count
 * that does not match the note type throws, and notes_v2 is direct SQL
 * over the notes table (only `mid=<id>` is understood here).
 */
class StandInAnkiDroid : ContentProvider() {
    data class Model(
        val name: String,
        val fields: List<String>,
        val deckId: Long?,
        val templates: MutableList<Triple<String, String, String>>,
    )

    data class StoredNote(val modelId: Long, val deckId: Long, val fields: List<String>)

    val decks = mutableMapOf(1L to "Default")
    val models = mutableMapOf<Long, Model>()
    val notes = mutableListOf<StoredNote>()
    var failure: RuntimeException? = null
    private var nextId = 1000L

    override fun onCreate() = true

    override fun getType(uri: Uri): String? = null

    private fun segments(uri: Uri) = uri.pathSegments

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        failure?.let { throw it }
        val columns = requireNotNull(projection)
        val cursor = MatrixCursor(columns)
        when (segments(uri)) {
            listOf("decks") -> decks.forEach { (id, name) ->
                cursor.addRow(columns.map { if (it == "deck_id") id else if (it == "deck_name") name else error(it) })
            }

            listOf("models") -> models.forEach { (id, model) ->
                cursor.addRow(columns.map { if (it == "_id") id else if (it == "name") model.name else error(it) })
            }

            listOf("notes_v2") -> {
                val mid = requireNotNull(selection).removePrefix("mid=").toLong()
                notes.filter { it.modelId == mid }.forEach { note ->
                    cursor.addRow(columns.map { if (it == "flds") note.fields.joinToString("\u001f") else error(it) })
                }
            }

            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        failure?.let { throw it }
        val v = requireNotNull(values)
        return when (segments(uri)) {
            listOf("decks") -> {
                val name = v.getAsString("deck_name")
                require(decks.values.none { it.equals(name, ignoreCase = true) }) { "Deck name already exists: $name" }
                val id = nextId++
                decks[id] = name
                Uri.withAppendedPath(uri, id.toString())
            }

            listOf("models") -> {
                val fields = v.getAsString("field_names").split("\u001f")
                val cards = v.getAsInteger("num_cards")
                val id = nextId++
                models[id] = Model(
                    name = v.getAsString("name"),
                    fields = fields,
                    deckId = v.getAsLong("deck_id"),
                    templates = MutableList(cards) { Triple("Card ${it + 1}", "{{${fields[0]}}}", "{{${fields[1]}}}") },
                )
                Uri.withAppendedPath(uri, id.toString())
            }

            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        failure?.let { throw it }
        val path = segments(uri)
        require(path.size == 4 && path[0] == "models" && path[2] == "templates") { "uri $uri is not supported" }
        val v = requireNotNull(values)
        require(!v.containsKey("model_id") && !v.containsKey("ord")) { "Updates to mid or ord are not allowed" }
        val model = requireNotNull(models[path[1].toLong()]) { "note type missing" }
        val ord = path[3].toInt()
        val old = model.templates[ord]
        model.templates[ord] = Triple(
            v.getAsString("card_template_name") ?: old.first,
            v.getAsString("question_format") ?: old.second,
            v.getAsString("answer_format") ?: old.third,
        )
        return 1
    }

    override fun bulkInsert(uri: Uri, values: Array<ContentValues>): Int {
        failure?.let { throw it }
        require(segments(uri) == listOf("notes")) { "uri $uri is not supported" }
        val deckId = requireNotNull(uri.getQueryParameter("deckId")) { "spec v1 path not modelled" }.toLong()
        values.forEach { v ->
            val mid = v.getAsLong("mid")
            val model = requireNotNull(models[mid]) { "note type missing: $mid" }
            val fields = v.getAsString("flds").split("\u001f")
            require(fields.size == model.fields.size) { "Incorrect flds argument" }
            notes += StoredNote(mid, deckId, fields)
        }
        return values.size
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("a send never deletes")
}
