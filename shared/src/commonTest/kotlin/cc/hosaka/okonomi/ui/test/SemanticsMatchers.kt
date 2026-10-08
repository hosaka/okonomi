package cc.hosaka.okonomi.ui.test

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/**
 * Matches a node by the label on its click action. `hasClickAction()`
 * matches every clickable on screen alike; the label is what an
 * accessibility service announces, so it says which of them was found.
 */
internal fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("click action labelled \"$label\"") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
