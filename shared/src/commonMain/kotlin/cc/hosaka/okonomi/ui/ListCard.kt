package cc.hosaka.okonomi.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf

/**
 * The container every scrolling list draws its rows in: a rounded
 * `surfaceContainer` panel, inset from the screen edge and separated
 * from its neighbours by the same gap everywhere.
 *
 * The colour is a scheme token rather than a tint or an alpha over the
 * background, so on Android 12+ it is the reader's own wallpaper-derived
 * palette (`appDynamicLightColorScheme`/`appDynamicDarkColorScheme`)
 * that decides it, and older Android and iOS get the Material defaults.
 * A hand-mixed lighter colour would be none of those.
 *
 * It sits one rung above the screen's `surface` on Material's container
 * ladder — `surfaceContainerLow` proved too close to the background to
 * separate a row from it. Note the ladder means "more separation", not
 * "lighter": in the dark theme this reads as a lighter panel, and in the
 * light theme as a slightly deeper one. That is the point, and it is why
 * the rung is named here rather than a brightness being nudged.
 *
 * It exists so the treatment has ONE definition. The Kanji tab grew this
 * look first and the other lists were each drawing rows their own way —
 * some padded flat against the background, the Phrases tab ruled off
 * with dividers — which read as three different screens. Copying the
 * `Surface` into each of them would have set that divergence in code
 * rather than fixing it, so every list now calls this instead: change
 * the panel here and Search, Favourites, Phrases and Kanji move together.
 *
 * [onClick] null means a tap goes nowhere, following the project's rule
 * that a null callback is a disabled action — the Names section of the
 * search results is the case that needs it, since a name has no entry
 * view to open.
 *
 * [onLongClick] is how every list offers copy-to-clipboard. A card with
 * only a long press still takes a press animation on a plain tap, which
 * is deliberate: the Kanji and Phrases cards used to be the only things
 * in the app that swallowed a touch silently, and looking dead is worse
 * than a ripple that resolves to nothing. Such a card is NOT given
 * `Role.Button` though, so an accessibility service is not told to
 * expect a click that does nothing; the long press announces itself
 * through [onLongClickLabel] instead.
 *
 * The ripple is clipped by the enclosing `Surface`, which clips to the
 * card's shape.
 *
 * The card owns its own spacing, outer and inner, so a caller adding a
 * row cannot accidentally set a different rhythm. Content is laid out in
 * a `ColumnScope`; a row that wants horizontal layout puts its own `Row`
 * inside.
 *
 * [contentPadding] is the inset between the panel edge and that content.
 * It is a parameter for one reason: the Forms tab's conjugation table
 * runs its rows the full width of the panel, and padding them would
 * leave the alternating row tint floating in a border of card colour
 * instead of banding the table. Rows pass nothing and get the standard
 * inset; a table passes `0.dp` and pads its own cells.
 */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    contentPadding: Dp = Dimens.contentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .then(interaction(onClick, onLongClick, onLongClickLabel))
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * The card's touch handling: nothing at all when neither callback is
 * given, and one `combinedClickable` when either is. One modifier rather
 * than a `clickable` plus something else, because a long press and a tap
 * on the same node have to be resolved together — two separate gesture
 * modifiers would race.
 */
@Composable
private fun interaction(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onLongClickLabel: String?,
): Modifier {
    if (onClick == null && onLongClick == null) return Modifier
    return Modifier.combinedClickable(
        role = if (onClick != null) Role.Button else null,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        // A card that only copies still answers a tap with a ripple; see
        // the KDoc above for why that is preferred to a dead card.
        onClick = onClick ?: {},
    )
}
