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
 * spec's on-device check. Nor can it show anything of Binder: Robolectric
 * calls the provider in-process, so an exception reaches the client as
 * the very object the stand-in threw, never through a Parcel, and no
 * transaction has a size limit. How a Parcel narrows exception types
 * and what a dead provider process returns are covered only by the
 * client's handling of them (a null cursor, a short count), here driven
 * directly.
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

    private fun send(notes: List<AnkiNote>) =
        sendToAnkiDroid(client, notes, { message, _ -> reports += message }, linkLabel = LINK_LABEL)

    private fun note(word: String, entryId: Long) =
        AnkiNote(word = word, reading = "よみ", meaning = "meaning", entryId = entryId)

    @Test
    fun `a first send builds the deck and both templates and inserts into that deck`() {
        val result = send(listOf(note("食べる", 1_358_280L), note("飲む", 1_169_870L)))

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), result, reports.toString())
        val deckId = anki.decks.entries.single { it.value == ANKI_DECK_NAME }.key
        val model = anki.models.values.single()
        assertEquals(AnkiNoteType.NAME, model.name)
        assertEquals(listOf("Word", "Reading", "Meaning", "EntryId"), model.fields)
        assertEquals(deckId, model.deckId)
        assertEquals(AnkiNoteType.templates(LINK_LABEL).map { Triple(it.name, it.front, it.back) }, model.templates)
        assertEquals(listOf(deckId, deckId), anki.notes.map { it.deckId })
        assertEquals(listOf("食べる", "よみ", "meaning", "1358280"), anki.notes.first().fields)
    }

    @Test
    fun `a re-send finds what is there by entry id and adds only the rest`() {
        send(listOf(note("食べる", 1_358_280L)))

        val result = send(listOf(note("食べる", 1_358_280L), note("飲む", 1_169_870L)))

        assertEquals(AnkiSendResult.Sent(added = 1, alreadyThere = 1), result)
        assertEquals(listOf("食べる", "飲む"), anki.notes.map { it.fields.first() })
        assertEquals(1, anki.decks.values.count { it == ANKI_DECK_NAME })
        assertEquals(1, anki.models.size)
    }

    @Test
    fun `two entries spelled alike both land and a re-send adds neither`() {
        val nama = listOf(note("生", 1_421_850L), note("生", 1_421_870L))

        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), send(nama))
        assertEquals(AnkiSendResult.NothingNew, send(nama))
        assertEquals(listOf("1421850", "1421870"), anki.notes.map { it.fields[3] })
    }

    @Test
    fun `notes of another note type are not mistaken for ours`() {
        anki.notes += StandInAnkiDroid.StoredNote(modelId = 999L, deckId = 1L, fields = listOf("食べる", "", "", "1358280"))

        val result = send(listOf(note("食べる", 1_358_280L)))

        assertEquals(AnkiSendResult.Sent(added = 1, alreadyThere = 0), result)
    }

    @Test
    fun `a refused permission becomes the permission dialog`() {
        anki.failure = SecurityException("Permission not granted for: query")

        assertEquals(AnkiSendResult.PermissionDenied(), send(listOf(note("食べる", 1L))))
    }

    @Test
    fun `storage not configured becomes the open AnkiDroid first dialog`() {
        anki.failure = IllegalStateException("AnkiDroid storage is not configured")

        assertEquals(AnkiSendResult.NoCollection, send(listOf(note("食べる", 1L))))
    }

    @Test
    fun `any other IllegalStateException is an ordinary reported failure`() {
        anki.failure = IllegalStateException("Collection is closed")

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), send(listOf(note("食べる", 1L))))
        assertTrue(reports.isNotEmpty())
    }

    @Test
    fun `an insert that lands short is a partial failure`() {
        anki.bulkInsertLands = 1

        val result = send(listOf(note("食べる", 1L), note("飲む", 2L), note("見る", 3L)))

        assertEquals(AnkiSendResult.Failed(added = 1, attempted = 3), result)
    }

    @Test
    fun `a note type of our name with other fields is not written into`() {
        anki.models[5L] = StandInAnkiDroid.Model(
            name = AnkiNoteType.NAME,
            fields = listOf("Word", "Reading", "Meaning"),
            deckId = null,
            templates = mutableListOf(Triple("Forward", "", ""), Triple("Reverse", "", "")),
        )

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), send(listOf(note("食べる", 1L))))
        assertTrue(anki.notes.isEmpty())
    }

    @Test
    fun `a template AnkiDroid did not write fails the send and the half-built type is not reused`() {
        anki.writesTemplates = false

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), send(listOf(note("食べる", 1L))))
        anki.writesTemplates = true
        // The placeholder cards it left are found by name next time, and refused.
        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), send(listOf(note("食べる", 1L))))
        assertTrue(anki.notes.isEmpty())
    }

    @Test
    fun `any other provider failure is a reported failure`() {
        anki.failure = IllegalArgumentException("uri is not supported")

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 0), send(listOf(note("食べる", 1L))))
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

    /** False to answer a template update as having written nothing. */
    var writesTemplates = true

    /** At most this many notes of a bulk insert land, when set. */
    var bulkInsertLands: Int? = null
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
                cursor.addRow(
                    columns.map {
                        when (it) {
                            "_id" -> id
                            "name" -> model.name
                            "field_names" -> model.fields.joinToString("\u001f")
                            else -> error(it)
                        }
                    },
                )
            }

            else -> if (segments(uri).size == 3 && segments(uri)[0] == "models" && segments(uri)[2] == "templates") {
                val model = requireNotNull(models[segments(uri)[1].toLong()]) { "note type missing" }
                model.templates.forEach { template ->
                    cursor.addRow(columns.map { if (it == "card_template_name") template.first else error(it) })
                }
            } else {
                queryNotes(uri, columns, selection, cursor)
            }
        }
        return cursor
    }

    private fun queryNotes(uri: Uri, columns: Array<String>, selection: String?, cursor: MatrixCursor) {
        when (segments(uri)) {
            listOf("notes_v2") -> {
                val mid = requireNotNull(selection).removePrefix("mid=").toLong()
                notes.filter { it.modelId == mid }.forEach { note ->
                    cursor.addRow(columns.map { if (it == "flds") note.fields.joinToString("\u001f") else error(it) })
                }
            }

            else -> throw IllegalArgumentException("uri $uri is not supported")
        }
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
        if (!writesTemplates) return 0
        val old = model.templates[ord]
        model.templates[ord] = Triple(
            v.getAsString("card_template_name") ?: old.first,
            v.getAsString("question_format") ?: old.second,
            v.getAsString("answer_format") ?: old.third,
        )
        // AnkiDroid counts the attributes it set, not rows.
        return listOf("card_template_name", "question_format", "answer_format").count { v.containsKey(it) }
    }

    override fun bulkInsert(uri: Uri, values: Array<ContentValues>): Int {
        failure?.let { throw it }
        require(segments(uri) == listOf("notes")) { "uri $uri is not supported" }
        val deckId = requireNotNull(uri.getQueryParameter("deckId")) { "spec v1 path not modelled" }.toLong()
        val landing = values.take(bulkInsertLands ?: values.size)
        landing.forEach { v ->
            val mid = v.getAsLong("mid")
            val model = requireNotNull(models[mid]) { "note type missing: $mid" }
            val fields = v.getAsString("flds").split("\u001f")
            require(fields.size == model.fields.size) { "Incorrect flds argument" }
            notes += StoredNote(mid, deckId, fields)
        }
        return landing.size
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("a send never deletes")
}

private const val LINK_LABEL = "More in Okonomi"
