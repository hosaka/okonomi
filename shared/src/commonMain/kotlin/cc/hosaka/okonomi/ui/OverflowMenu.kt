package cc.hosaka.okonomi.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The panel every toolbar overflow menu opens, so the app has one menu
 * rather than one per screen.
 *
 * The shape is the token the list rows are cut with, not a radius in dp:
 * a menu opening over the Favourites cards with squarer corners than
 * them read as a system menu that had landed on the screen rather than
 * as part of it. Naming the token means retuning `shapes.large` moves
 * the cards and the menus together — the argument [ListCard] makes for
 * naming its colour instead of mixing one.
 *
 * The width floor exists because these menus hold two or three short
 * labels. Material's own minimum is 112dp, narrower than most of them,
 * which leaves the rounded corners crowding the text and the whole panel
 * looking like an accident. A longer label still grows past the floor.
 *
 * Only the panel is shared. The button that opens it stays with each
 * menu: what it is called, and when it is disabled, is that screen's
 * business, and pulling those in here would mean a parameter per caller.
 */
@Composable
fun OverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.widthIn(min = MENU_MIN_WIDTH),
        content = content,
    )
}

private val MENU_MIN_WIDTH = 200.dp
