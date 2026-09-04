package cc.hosaka.okonomi.user

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The only file format version that exists. A file written now has to be
 * refusable by a later app that changed the format; without the field
 * the only failure mode left is a silent misread.
 */
private const val FAVOURITES_FILE_VERSION = 1

/**
 * The whole of the exported file:
 *
 * ```json
 * { "version": 1, "name": "Favourites", "entries": [1207610, 1000220] }
 * ```
 *
 * `entries` are JMdict `ent_seq` ids in display order, newest first.
 * Nothing else is written: resolved words, readings and glosses all come
 * from the bundled dictionary and would only go stale in a file.
 *
 * [name] carries a default because it is written and then discarded —
 * an import always lands in the shipped Favourites list, so a file
 * missing the field is still perfectly importable. [version] has no
 * default on purpose: a file that does not say what it is cannot be
 * read.
 */
@Serializable
private data class FavouritesFile(
    val version: Int,
    val name: String = FAVOURITES_LIST_NAME,
    val entries: List<Long>,
)

/**
 * Lenient about keys a later version might add, strict about everything
 * else: an id that is not a number, or a missing `entries`, is a file
 * this app cannot honestly import.
 */
private val favouritesJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    // Without this the list name is left out of any file whose list is
    // still called what it shipped as, which is every file anyone can
    // write today. A format that describes itself has to describe itself
    // in the ordinary case too.
    encodeDefaults = true
}

/**
 * The file contents for [entryIds], which must already be in display
 * order (newest first) — the raw saved ids, including any the bundled
 * dictionary no longer carries.
 */
fun encodeFavourites(
    entryIds: List<Long>,
    name: String = FAVOURITES_LIST_NAME,
): String = favouritesJson.encodeToString(
    FavouritesFile(
        version = FAVOURITES_FILE_VERSION,
        name = name,
        entries = entryIds,
    ),
)

/**
 * The entry ids in [text], in display order, or null when it is not a
 * file this app can read: not JSON, not version 1, no `entries`, or an
 * entry that is not a number.
 *
 * Duplicates are dropped, keeping the first occurrence's position, which
 * is what storing the list does anyway — `list_entry`'s primary key
 * makes the second insert of an id a no-op.
 *
 * An empty `entries` is valid and means an empty list, not a failure.
 * Ids are taken as they are: one the bundled dictionary no longer
 * carries imports like any other and is simply not rendered.
 */
fun decodeFavourites(text: String): List<Long>? = try {
    val file = favouritesJson.decodeFromString<FavouritesFile>(text)
    if (file.version != FAVOURITES_FILE_VERSION) null else file.entries.distinct()
} catch (e: Exception) {
    // Everything kotlinx.serialization throws for a file that is not
    // ours is the same answer to the reader, and the caller turns it
    // into the one dialog this feature has.
    null
}
