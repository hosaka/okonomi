package cc.hosaka.okonomi.feature.forms

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.lang.Form

/**
 * One table: the class name as the reader sees it, and its rows.
 * [className] is the `tag_label` text for the JMdict code, or the code
 * itself until — or unless — that label arrives.
 */
@Immutable
data class ConjugationTable(
    val className: String,
    val forms: List<Form>,
)

@Immutable
data class FormsTabState(
    val content: FormsTabContentState,
)

/**
 * The body of the entry view's Forms tab. There is no loading state and
 * no error state here, and that is the point: every cell is computed
 * from the entry the screen already holds, so the table can be drawn
 * the instant the tab is composed. The only thing this tab reads from
 * the dictionary is the class name at the top of each table, which is
 * cosmetic — it upgrades in place when it arrives, and its absence
 * leaves the JMdict code standing in for it rather than replacing a
 * correct table with a full-screen failure.
 */
@Immutable
sealed interface FormsTabContentState {
    /**
     * [takesSuru] is the `vs` case, far and away the most common way to
     * reach this state. The reader is not looking at a word that
     * refuses to inflect; they are looking at a noun whose verb is
     * noun + する, and the message says so instead of leaving them to
     * guess why the table is missing.
     */
    data class NotConjugable(
        val takesSuru: Boolean,
    ) : FormsTabContentState

    data class Ready(
        val tables: List<ConjugationTable>,
    ) : FormsTabContentState
}
