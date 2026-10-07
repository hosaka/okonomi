package cc.hosaka.okonomi.anki

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.feature.search.writtenForm

/**
 * One saved word as an AnkiDroid note: the four fields of
 * [AnkiNoteType], the first three already in the HTML Anki stores.
 * [word] stays field 0, the one AnkiDroid sorts and checks on; [entryId]
 * is field 3, and it is what decides whether a word is already there, so
 * two entries spelled alike are two notes.
 */
@Immutable
data class AnkiNote(
    val word: String,
    val reading: String,
    val meaning: String,
    val entryId: Long,
) {
    val fields: List<String> get() = listOf(word, reading, meaning, entryId.toString())
}

/**
 * The card for a saved row. Word is the written form with no furigana;
 * Reading is the kana; Meaning is built from [glosses] — the entry's
 * glosses in order (see `entryGlosses`), not the row's sense lines —
 * and capped by [ankiMeaning]. No furigana
 * markup anywhere: a card is studied, and ruby over the Word would give
 * the answer away.
 */
fun SearchHit.toAnkiNote(glosses: List<String>): AnkiNote = AnkiNote(
    word = ankiFieldHtml(writtenForm()),
    reading = ankiFieldHtml(kanaReading()),
    meaning = ankiFieldHtml(ankiMeaning(glosses.ifEmpty { senseLinesWithoutMore() })),
    entryId = entryId,
)

/**
 * The row's own lines, for an entry the gloss read had nothing for (a
 * dictionary that dropped the entry between the row and the send):
 * something on the card rather than an empty Meaning. The row's " …"
 * says "more on the entry screen", which on a card means nothing.
 */
private fun SearchHit.senseLinesWithoutMore(): List<String> =
    senseLines.map { it.removeSuffix(SENSE_LINE_MORE) }.filter { it.isNotBlank() }

/** What a row's last sense line ends in when senses were left off; see EntrySearch's senseLines. */
private const val SENSE_LINE_MORE = " …"

/**
 * Five glosses keep a card readable; the full entry is a tap away
 * through the card's "More in Okonomi" link (Alex, 2026-10-07).
 */
internal const val ANKI_MEANING_GLOSS_LIMIT = 5

/**
 * A plain dash list, one gloss per line in the order given, at most
 * [ANKI_MEANING_GLOSS_LIMIT] of them and with no marker when more were
 * dropped. Plain text rather than `<ul>`, so the card reads the same in
 * every Anki client and theme; [ankiFieldHtml] turns the lines into
 * `<br>`s.
 */
internal fun ankiMeaning(glosses: List<String>): String =
    glosses.take(ANKI_MEANING_GLOSS_LIMIT).joinToString("\n") { "- $it" }

/**
 * The kana reading of a row, taken from its title segments as
 * `buildHits` makes them: a written form followed by its reading, or a
 * single segment for a word with no kanji form (and for one whose form
 * is its reading, which the row does not repeat).
 *
 * Every segment after the first is a reading, whether it sits over the
 * form or beside it; should a title ever carry more than one, they are
 * all kept, joined the way Japanese lists alternatives. A lone segment
 * is the reading.
 */
internal fun SearchHit.kanaReading(): String {
    val readings = titleSegments.drop(1).map { it.text }.ifEmpty { titleSegments.take(1).map { it.text } }
    return readings.joinToString("、")
}

/**
 * Plain text as an Anki field. Anki renders fields as HTML, so the three
 * characters that would be read as markup are escaped and line breaks
 * become `<br>`. The unit separator is dropped outright: it is the
 * character AnkiDroid joins fields with, and one inside a field would
 * split it in two.
 */
fun ankiFieldHtml(text: String): String = text
    .replace(ANKI_FIELD_SEPARATOR, "")
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\r\n", "\n")
    .replace("\n", "<br>")

/** What AnkiDroid joins a note's fields with in its provider. */
internal const val ANKI_FIELD_SEPARATOR = "\u001f"
