package cc.hosaka.okonomi.feature.kanji

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingFourth
import cc.hosaka.okonomi.ui.theme.horizontalPaddingHalf
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
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
 * Which character the detail overlay is showing, or null for closed.
 *
 * The selection is the only part of this feature a test can stand on.
 * Both ways the overlay closes — a tap outside it and system back — are
 * `DialogProperties` defaults fired by the framework, and neither can be
 * driven from a host test (see [KanjiDetailDialog]). Routing
 * `onDismissRequest` through [dismiss] rather than through a raw
 * `mutableStateOf` at the call site is what keeps the transition they
 * cause under test even though the gestures are not.
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
 * No `properties` are passed, and all three defaults are taken
 * deliberately. `dismissOnBackPress` and `dismissOnClickOutside` are
 * both on, which is the whole dismissal behaviour; `dismissOnBackPress`
 * is also why there is no `NavigationBackHandler` here, since a second
 * handler would fight it for the same press. The third,
 * `usePlatformDefaultWidth`, is what sizes the window: it caps the
 * surface at the platform's own dialog width, which is narrower than
 * anything this content wants, so the surface states no width of its
 * own. A `widthIn` here would be dead code — the platform cap binds
 * first on every screen size the app runs at.
 *
 * **Neither dismissal is asserted anywhere, and that is not an
 * oversight.** Nothing in this repo can dispatch a system back press,
 * and a dialog's scrim carries no semantics node for a test to tap, so
 * the honest position is to say so rather than to write an assertion
 * that would pass whether the properties were set or not. What is tested
 * is the transition they trigger, through [KanjiDetailDialogState]. The
 * gestures themselves are checked on a device.
 */
@Composable
internal fun KanjiDetailDialog(
    state: KanjiDetailDialogState,
    onRadicalClick: (String) -> Unit,
) {
    val character = state.character ?: return
    val paneTitle = stringResource(Res.string.entry_kanji_detail_title, character.literal)
    Dialog(onDismissRequest = { state.dismiss() }) {
        Surface(
            // Without a pane title the overlay arrives silently: a
            // screen reader is given a new window with nothing saying
            // what covered the screen or which character it belongs to.
            modifier = Modifier
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
            horizontalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
            verticalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
        ) {
            radicals.forEach { radical ->
                RadicalChip(
                    radical = radical,
                    onClick = { onRadicalClick(radical) },
                )
            }
        }
    }
}

/**
 * Minimum side of a radical target. A chip sized to one character is
 * smaller than a fingertip, and these sit side by side.
 */
private val RADICAL_TAP_SIZE = 48.dp

@Composable
private fun RadicalChip(
    radical: String,
    onClick: () -> Unit,
) {
    // Names the radical it searches for, because the chip's only text is
    // the character itself and "button, 心" says nothing about the tap.
    val label = stringResource(Res.string.entry_kanji_radical_search, radical)
    // Clipped before the gesture, not after: `Surface` clips its own
    // content to the shape but not a modifier applied above it, so a
    // ripple attached outside that clip draws a rectangle over a rounded
    // chip. The same ordering `ListCard` documents, where the enclosing
    // `Surface` is what clips the card's ripple.
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .sizeIn(minWidth = RADICAL_TAP_SIZE, minHeight = RADICAL_TAP_SIZE)
            .clip(shape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = Dimens.horizontalPaddingHalf),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = radical,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
