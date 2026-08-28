package cc.hosaka.okonomi.feature.word

import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.lang.conjugations
import cc.hosaka.okonomi.lang.hanCharacters

/**
 * The tabs this entry actually has something to show on.
 *
 * A tab with nothing in it used to be drawn anyway, holding a line of
 * text saying so — a swipe that costs a screen to be told there is
 * nothing on it. Now it is not drawn at all.
 *
 * **Every clause calls the same function the tab itself calls.** That is
 * the whole design: `hanCharacters` is what the Kanji tab's producer
 * uses to decide it has no characters, and `conjugations` is what the
 * Forms tab's producer uses to decide a word does not conjugate. A
 * second rule that merely agreed with them today would drift, and the
 * failure would be silent in both directions — a tab hidden with content
 * behind it, or a tab shown with the message back.
 *
 * Word is always present: an entry with no readings and no forms cannot
 * be loaded at all, so there is always something to show there, and the
 * view would otherwise have to handle having no tabs.
 *
 * [EntryDetail.hasSentences] is the odd one out because it cannot be
 * computed here — see its own KDoc for why it is carried on the entry.
 */
fun availableTabs(entry: EntryDetail): List<EntryTab> = EntryTab.entries.filter { tab ->
    when (tab) {
        EntryTab.Word -> true
        EntryTab.Kanji -> hanCharacters(entry.headword).isNotEmpty()
        EntryTab.Forms -> conjugations(entry.headword, entry.posCodes).isNotEmpty()
        EntryTab.Phrases -> entry.hasSentences
    }
}
