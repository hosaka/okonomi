package cc.hosaka.okonomi.feature.word

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.ui.FloatingTabBarDefaults
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_save
import okonomi.shared.generated.resources.entry_saved
import okonomi.shared.generated.resources.entry_unsaved
import org.jetbrains.compose.resources.stringResource

object SaveEntryButtonDefaults {
    /** The Material FAB container, which this button does not resize. */
    private val size: Dp = 56.dp

    /** Gap above the tab bar, matching the bar's own segment spacing. */
    private val spacing: Dp = FloatingTabBarDefaults.itemSpacing

    /** Inset from the trailing window edge. */
    val horizontalPadding: Dp = FloatingTabBarDefaults.horizontalPadding

    /**
     * Where the button's bottom edge sits: exactly the padding a
     * scrolling tab already leaves for the tab bar, which is the bar's
     * height plus a gap plus the bottom inset. That places the button in
     * the gap directly above the bar rather than on top of it — the bar
     * is `align(BottomCenter)` and up to 448.dp wide, so the bottom-right
     * corner is the bar's on a phone.
     */
    val bottomPadding: Dp
        @Composable
        get() = FloatingTabBarDefaults.contentBottomPadding

    /**
     * Bottom content padding a scrolling tab needs with the button
     * present: everything the tab bar already asked for, plus the button
     * standing above it. Without this the last sentence of the Phrases
     * tab cannot be scrolled out from under the button.
     */
    val contentBottomPadding: Dp
        @Composable
        get() = FloatingTabBarDefaults.contentBottomPadding + size + spacing
}

/**
 * Saves the entry to Favourites, or takes it out again.
 *
 * A floating action button at the bottom right, above the tab bar
 * (Alex's ruling — thumb reach, the same reasoning as double-tap-to-
 * focus). It is drawn once for the whole entry view rather than per tab:
 * it saves the entry, not the tab, so it stands still while the pager
 * moves under it.
 *
 * The state is whatever storage says, never an optimistic local flag:
 * a write that could not land leaves the button unsaved, which is the
 * honest answer. It is what the button *draws*, and deliberately not
 * what it decides from; see `FavouritesStore.toggleFavourite`.
 */
@Composable
fun SaveEntryButton(
    isFavourite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.entry_save)
    val state = stringResource(if (isFavourite) Res.string.entry_saved else Res.string.entry_unsaved)
    FloatingActionButton(
        // The tap says "the other one", not "make it true" or "make it
        // false": [isFavourite] is what the last read of storage said,
        // which a second tap in the same frame — or a first tap before
        // that read has come back — would compute the wrong answer from.
        // The flip happens inside the write's transaction instead.
        onClick = onToggle,
        modifier = modifier
            // No separate inset padding: [bottomPadding] is the tab
            // bar's own content padding, which already carries the
            // bottom system-bar inset. Adding it again would push the
            // button a navigation bar's height too high.
            .padding(
                end = SaveEntryButtonDefaults.horizontalPadding,
                bottom = SaveEntryButtonDefaults.bottomPadding,
            )
            // One node, announced as "Save word" with "saved" or "not
            // saved" as its state, instead of an unlabelled button whose
            // only clue is which of two icons it happens to be drawing.
            .semantics {
                stateDescription = state
            },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(),
    ) {
        Crossfade(targetState = isFavourite) { favourite ->
            Icon(
                imageVector = if (favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = label,
            )
        }
    }
}
