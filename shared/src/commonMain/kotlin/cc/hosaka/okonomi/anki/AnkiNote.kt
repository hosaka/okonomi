package cc.hosaka.okonomi.anki

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.feature.search.writtenForm

/**
 * One saved word as an AnkiDroid note: the three fields of
 * [AnkiNoteType], already in the HTML Anki stores. [word] is field 0,
 * the one AnkiDroid's duplicate check keys on, so it is also what decides
 * whether a word is already there.
 */
@Immutable
data class AnkiNote(
    val word: String,
    val reading: String,
    val meaning: String,
) {
    val fields: List<String> get() = listOf(word, reading, meaning)
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
    meaning = ankiFieldHtml(ankiMeaning(glosses)),
)

/** Alex, 2026-10-07: five keeps a card readable. */
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
 * The kana reading of a row, taken from its title segments: a row is
 * the written form followed by its reading, or the reading alone for a
 * word with no kanji form (and for one whose only form is its reading,
 * which the row does not repeat). The reading segment counts whether it
 * sits over the form or beside it — it is the reading either way.
 */
internal fun SearchHit.kanaReading(): String =
    (titleSegments.getOrNull(1) ?: titleSegments.firstOrNull())?.text.orEmpty()

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
