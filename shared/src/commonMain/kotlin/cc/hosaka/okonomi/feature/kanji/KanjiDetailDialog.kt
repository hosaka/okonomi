package cc.hosaka.okonomi.feature.kanji

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.ui.CharacterChip
import cc.hosaka.okonomi.ui.screenMaxWidth
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingFourth
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_kanji_detail_close
import okonomi.shared.generated.resources.entry_kanji_detail_title
import okonomi.shared.generated.resources.entry_kanji_radical_search
import okonomi.shared.generated.resources.entry_kanji_section_name
import okonomi.shared.generated.resources.entry_kanji_section_radical
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the character has anything the overlay could show. Nanori and
 * the radicals are all it holds, so a character with neither must not
 * offer the tap that opens it — which includes every `hasData == false`
 * character that radkfile also says nothing about.
 *
 * A property rather than a check spelled out at the call site: the card
 * that hides the gesture and the dialog that would be empty have to
 * agree, and one definition is how they cannot drift.
 */
internal val KanjiCharacter.hasDetailToShow: Boolean
    get() = nameReadings.isNotEmpty() || radicals.isNotEmpty()

/**
 * What the dialog surface leaves either side of itself once
 * `usePlatformDefaultWidth` is off and the window fills the screen.
 *
 * Smaller than the platform's own 48.dp dialog margin, which is what was
 * wrapping the radical row a character early.
 */
internal val DIALOG_MARGIN = 24.dp

/**
 * Which character the detail overlay is showing, or null for closed.
 *
 * The selection is the only part of this feature a test can stand on,
 * and both ways the overlay closes run through [dismiss]. A tap outside
 * the surface is [KanjiDetailDialog]'s own gesture, so that one is
 * driven directly in `KanjiDetailDialogUiTest`. System back is not: it
 * arrives through `dismissOnBackPress` and nothing in this repo can
 * dispatch the press, so what a host test can still see is the
 * transition it causes, which is what `KanjiDetailDialogStateTest`
 * asserts. Routing
 * `onDismissRequest` through [dismiss] rather than through a raw
 * `mutableStateOf` at the call site is what keeps that reachable.
 */
@Stable
internal class KanjiDetailDialogState {
    var character: KanjiCharacter? by mutableStateOf(null)
        private set

    fun show(character: KanjiCharacter) {
        this.character = character
    }

    fun dismiss() {
        character = null
    }
}

@Composable
internal fun rememberKanjiDetailDialogState(): KanjiDetailDialogState =
    remember { KanjiDetailDialogState() }

/**
 * The nanori and radicals of one character, over the entry screen.
 *
 * A `Dialog` and deliberately not a `ModalBottomSheet`, a `Popup` or a
 * scrim drawn inside the tab. Compose Multiplatform renders `Dialog` in
 * the same window as the rest of the UI, so there is no separate-window
 * cost on iOS, and it brings a dimming scrim that covers the toolbar and
 * the floating tab bar — which a scrim drawn inside the tab's own
 * subtree could never reach.
 *
 * `dismissOnBackPress` is left on and owns system back on its own, which
 * is why there is no `NavigationBackHandler` here: a second handler
 * would fight it for the same press.
 *
 * `usePlatformDefaultWidth` is turned OFF, and that is what lets the
 * surface state a width at all. Left on, the platform caps the window at
 * its own dialog width — 315.dp on a 411.dp screen — and the radical row
 * wraps a character early: `動`'s six radicals need 308.dp of chips and
 * gaps, and only 283.dp survives the content padding, so `里` was pushed
 * onto a row of its own beside a column of dead space. Off, the window
 * fills the screen and [DIALOG_MARGIN] is what insets the surface
 * instead, which leaves room for the sixth chip.
 *
 * **`dismissOnClickOutside` is inert here, and the outside tap is this
 * composable's own.** The property is still on because it is the
 * default, not because it does anything: it fires only for a touch
 * outside the dialog *window*, and once `usePlatformDefaultWidth` is off
 * the window is the whole screen, so there is nowhere left to put such a
 * touch. What dismisses instead is [DismissLayer], the first child of
 * the centring Box below.
 *
 * Three things about that arrangement have to survive any later layout
 * change, and none of them is visible from the rendered result:
 *
 * - The layer is declared BEFORE the `Surface`, so it draws beneath and
 *   hit testing reaches it only where the surface was missed. Material3
 *   gives a `Surface` an empty `pointerInput` even with no `onClick`, so
 *   a hit stops there and never falls through to a sibling below it —
 *   read out of the Material3 source at Compose Multiplatform 1.11.1,
 *   material3 1.11.0-alpha07, and covered by a test rather than trusted.
 * - [DIALOG_MARGIN] is padding on the `Surface`, not on the Box. A
 *   caller's `Modifier.padding` sits outside the surface's own pointer
 *   node, so the margin strips miss it and reach the layer — which is
 *   the only reason a tap beside the surface closes the overlay. Moved
 *   back onto the Box, those strips become dead.
 * - The gesture must stay on a child rather than moving up onto the Box
 *   around both. The surface consumes nothing, so an ancestor's
 *   `detectTapGestures` would fire for taps on the surface too, and the
 *   overlay would close whenever it was touched.
 *
 * **System back is still asserted nowhere, and that is not an
 * oversight.** Nothing in this repo can dispatch the press, so the
 * honest position is to say so rather than to write an assertion that
 * would pass whether `dismissOnBackPress` held or not; what is tested is
 * the transition it triggers, through [KanjiDetailDialogState]. The
 * outside tap is no longer in that category — it is a composable of our
 * own now, and `KanjiDetailDialogUiTest` taps it.
 */
@Composable
internal fun KanjiDetailDialog(
    state: KanjiDetailDialogState,
    onRadicalClick: (String) -> Unit,
) {
    val character = state.character ?: return
    val paneTitle = stringResource(Res.string.entry_kanji_detail_title, character.literal)
    Dialog(
        onDismissRequest = { state.dismiss() },
        // All three properties written out, defaults included. An
        // unexamined default is what shipped an overlay that could not
        // be dismissed by touch, so what this dialog relies on should be
        // readable here rather than only in the KDoc. The values are
        // exactly the defaults: dismissOnClickOutside stays ON although
        // it is inert while the window fills the screen, because turning
        // it off would remove a harmless idempotent second path on any
        // platform or window where it is not.
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // The window fills the screen once usePlatformDefaultWidth is
        // off, so centring is this Box's job rather than the dialog's:
        // capping the surface below the width fillMaxWidth offered it
        // leaves it anchored to the start otherwise, which put 24.dp of
        // margin down one side of the screen and 47.dp down the other.
        //
        // No pointer input on this Box. The dismiss gesture belongs to
        // the child below it, for the reason the KDoc gives.
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            DismissLayer(literal = character.literal, onDismiss = state::dismiss)
            Surface(
                // Without a pane title the overlay arrives silently: a
                // screen reader is given a new window with nothing saying
                // what covered the screen or which character it belongs to.
                //
                // The width is the surface's own now that the platform cap
                // is off: DIALOG_MARGIN either side, and never wider than
                // the rest of the app's content, so a tablet gets a dialog
                // rather than a full-bleed sheet.
                //
                // The margin is padding HERE and not on the Box, which
                // is what gives the strips it leaves to [DismissLayer];
                // the KDoc above says why that is load-bearing.
                //
                // widthIn BEFORE fillMaxWidth: fillMaxWidth fixes the
                // incoming width, after which widthIn can only coerce its
                // max back up to that fixed value and does nothing at all.
                // Constrain first, then fill what the constraint allows.
                modifier = Modifier
                    .padding(horizontal = DIALOG_MARGIN)
                    .widthIn(max = screenMaxWidth)
                    .fillMaxWidth()
                    .semantics { this.paneTitle = paneTitle },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                KanjiDetailContent(
                    character = character,
                    onRadicalClick = onRadicalClick,
                    // The scroll belongs to the window, not to the content:
                    // kanjidic gives some characters a long nanori list, and
                    // on a short screen the surface has to give way rather
                    // than run off the bottom of it.
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/**
 * Everything the overlay's surface does not cover, as one tap target
 * that closes it.
 *
 * `matchParentSize` rather than `fillMaxSize`: the layer has to span the
 * window without having any say in how big the window's Box is, and
 * `fillMaxSize` on a child would make it a measured sibling of the
 * surface.
 *
 * It carries a name and a close action as well as the gesture. A finger
 * can aim at "outside the surface"; a screen reader cannot, so without
 * them the overlay would offer a reader nothing but system back, and the
 * layer would sit in the tree as an unlabelled full-screen target. It
 * names [literal] for the same reason the pane title does: the card that
 * said which character this is went behind the scrim when the overlay
 * opened.
 *
 * `onClick` takes no label of its own. The description already names the
 * action, and a label would have an accessibility service read it a
 * second time as the hint — "Close 食 details, double-tap to Close 食
 * details". Material3's own scrim passes none either. The action is a
 * second route to the same [onDismiss] rather than the only one; the
 * pointer gesture is what a tap uses.
 *
 * `traversalIndex` is meant to put the layer after the surface, so a
 * reader hears the character's details before being offered the way out
 * of them. That is reasoned rather than observed: Material3's `Surface`
 * sets the deprecated `IsContainer` key rather than `IsTraversalGroup`,
 * and iOS orders its accessibility elements through a different
 * implementation again, so what a host test can pin is that the property
 * is set — the resulting order is a device check on both platforms.
 */
@Composable
private fun BoxScope.DismissLayer(literal: String, onDismiss: () -> Unit) {
    val closeLabel = stringResource(Res.string.entry_kanji_detail_close, literal)
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(onDismiss) {
                detectTapGestures { onDismiss() }
            }
            .semantics {
                contentDescription = closeLabel
                traversalIndex = 1f
                onClick {
                    onDismiss()
                    true
                }
            },
    )
}

/**
 * The overlay's content without the window around it, so a preview and
 * a reader can both see it without a dialog host.
 *
 * The literal heads it because the card underneath is covered by the
 * scrim: without it the overlay names no character.
 */
@Composable
internal fun KanjiDetailContent(
    character: KanjiCharacter,
    onRadicalClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(Dimens.contentPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.verticalPaddingHalf),
    ) {
        Text(
            text = character.literal,
            style = MaterialTheme.typography.displaySmall,
            // The one thing naming the overlay's subject, so a screen
            // reader can jump to it rather than walking the surface.
            modifier = Modifier
                .semantics { heading() },
        )
        LabelledLine(
            label = stringResource(Res.string.entry_kanji_section_name),
            values = character.nameReadings,
            join = READING_JOIN,
        )
        RadicalLine(
            radicals = character.radicals,
            onRadicalClick = onRadicalClick,
        )
    }
}

/**
 * The radicals as one target each, which is the point of the overlay:
 * a radical is where the reader goes next, and a range inside a single
 * joined line is not something a finger can be aimed at. The same split
 * the Phrases tab makes for its sentence words, and it costs the same
 * thing — a screen reader now walks N siblings rather than hearing one
 * line — bought back here by each chip naming its own action.
 *
 * The values are radkfile's own: representative kanji such as 忙 and 心,
 * never rewritten into the CJK Radical Supplement forms 忄 and ⺗. What
 * is searched for has to be a character the dictionary actually carries.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadicalLine(
    radicals: List<String>,
    onRadicalClick: (String) -> Unit,
) {
    if (radicals.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        SectionLabel(stringResource(Res.string.entry_kanji_section_radical))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth(),
            // Centred. A row of fixed-width chips almost never divides
            // the surface exactly, and start-alignment banks the whole
            // remainder on one side, which reads as a gap after the last
            // chip rather than as margin. Split, it is margin.
            horizontalArrangement = Arrangement.spacedBy(
                Dimens.horizontalPaddingFourth,
                Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
        ) {
            radicals.forEach { radical ->
                CharacterChip(
                    character = radical,
                    // Per radical, not hoisted: the label names the
                    // character it opens, so the spoken action hint
                    // identifies its own chip in a row of them.
                    onClickLabel = stringResource(
                        Res.string.entry_kanji_radical_search,
                        radical,
                    ),
                    onClick = { onRadicalClick(radical) },
                )
            }
        }
    }
}
