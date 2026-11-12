package cc.hosaka.okonomi.feature.forms

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.lang.FormId
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment

/**
 * One table: its class's JMdict code (`v1`, `v5k`), and its rows. The
 * code, not a name: the screen heads the table with the app's own name
 * for the code in the UI language (see
 * `cc.hosaka.okonomi.ui.labels.tagLabel`), and a name resolved here
 * would keep the language it was resolved in.
 */
@Immutable
data class ConjugationTable(
    val classCode: String,
    val rows: List<ConjugationRow>,
)

/**
 * One row of a table, as furigana: the row's identity and its two cells
 * already split into the runs their readings sit over. A null [negative]
 * means the row has no negative cell at all (see [cc.hosaka.okonomi.lang.Form]).
 *
 * The readings are carried per cell rather than derived at render time
 * because whether a cell gets one is a property of the whole table: a
 * stem that reads the same way in every row is left plain, and only a
 * stem that shifts — 為る's す/し/さ — is worth the ruby.
 */
@Immutable
data class ConjugationRow(
    val id: FormId,
    val affirmative: List<FuriganaSegment>,
    val negative: List<FuriganaSegment>?,
)

@Immutable
data class FormsTabState(
    val content: FormsTabContentState,
)

/**
 * The body of the entry view's Forms tab. There is no loading state and
 * no error state here, and that is the point: every cell is computed
 * from the entry the screen already holds, so the table can be drawn
 * the instant the tab is composed. Nothing here is read from the
 * dictionary, the class heading included: that is the app's own name
 * for the class's code.
 */
@Immutable
sealed interface FormsTabContentState {
    /**
     * [takesSuru] is the `vs` case, far and away the most common way to
     * reach this state. The reader is not looking at a word that
     * refuses to inflect; they are looking at a noun whose verb is
     * noun + する, and the message says so instead of leaving them to
     * guess why the table is missing.
     *
     * **Not reachable through the tab bar any more.** `availableTabs`
     * hides the Forms tab when `conjugations` returns nothing, which is
     * the very call that produces this state, so a reader cannot arrive
     * here. Kept rather than deleted: if the visibility rule and this
     * check ever drift apart, the reader gets the explanation instead of
     * a blank screen.
     */
    data class NotConjugable(
        val takesSuru: Boolean,
    ) : FormsTabContentState

    data class Ready(
        val tables: List<ConjugationTable>,
    ) : FormsTabContentState
}
