package cc.hosaka.okonomi.feature.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cc.hosaka.okonomi.ui.coach.CoachTarget
import cc.hosaka.okonomi.ui.coach.coachMarkTarget
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.search_filters_default
import okonomi.shared.generated.resources.search_filters_names_on
import okonomi.shared.generated.resources.search_names_toggle
import okonomi.shared.generated.resources.search_options
import okonomi.shared.generated.resources.search_options_close
import org.jetbrains.compose.resources.stringResource

internal object SearchFiltersButtonDefaults {
    /** The toggle button's container, which this screen does not resize. */
    private val size: Dp = 56.dp

    /**
     * The gap between the button and the screen's bottom and trailing
     * edges. `FloatingActionButtonMenu` lays this out itself, so it is
     * named here only for the padding below rather than applied again.
     */
    private val margin: Dp = 16.dp

    /**
     * Bottom content padding the results list needs so that its last row
     * and its paging footer can be scrolled clear of the button.
     */
    val contentBottomPadding: Dp = size + margin * 2
}

/** Test tag on the dot drawn while a filter is on and the menu is closed. */
internal const val SEARCH_FILTERS_BADGE_TAG = "search-filters-badge"

/**
 * The search screen's filters, as a floating action button menu at the
 * bottom-end of the screen: a toggle button that expands a stack of
 * filter pills. Names is the only pill today.
 *
 * Where this sits is Alex's call twice over. On 2026-08-26 it was "Leave
 * it hidden": the Names toggle went into an overflow menu at the trailing
 * edge of the field, because names are a departure from the default
 * rather than a mode the reader picks between. On 2026-10-06 he moved it
 * here and made it visible — the three-dot icon was far from the thumb
 * and too small to hit, and more filters are planned for the same place.
 *
 * What the first ruling still forbids stands: a reader searching たなが —
 * a real surname that no dictionary word matches — sees an empty result
 * with nothing hinting that a filter would have answered it. Do not add a
 * hint to the empty state, an entry in Settings, or a reworded "no
 * results". The button's badge says a filter is on; nothing says one
 * should be.
 *
 * The menu stays open while pills are toggled, so several filters can be
 * set in one visit, and closes only through the button or system back.
 * There is deliberately no outside-tap scrim. Back is taken only while
 * the menu is open: on the Search root, back otherwise switches to the
 * default tab, which would leave this menu open on a tab no longer shown.
 *
 * A null [onNamesEnabledChange] draws nothing at all. A floating action
 * button has no disabled state, and one that does nothing when tapped
 * would be worse than none.
 *
 * [onExpandedChange] hears every open and close, so the screen can hide
 * the idle coach marks while the menu is up. The menu still owns its own
 * state; leaving composition while open reports it closed.
 * [isCoachMarkTarget] makes the button the filters coach mark's target.
 */
@Composable
internal fun SearchFiltersButton(
    namesEnabled: Boolean,
    onNamesEnabledChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit = {},
    isCoachMarkTarget: Boolean = false,
) {
    if (onNamesEnabledChange == null) return

    var expanded by remember { mutableStateOf(false) }
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val setExpanded = { value: Boolean ->
        expanded = value
        currentOnExpandedChange(value)
    }
    DisposableEffect(Unit) {
        onDispose {
            if (expanded) currentOnExpandedChange(false)
        }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = expanded,
        onBackCompleted = { setExpanded(false) },
    )

    // Named for what a tap does next: open the options, or close them.
    val label = stringResource(
        if (expanded) Res.string.search_options_close else Res.string.search_options,
    )
    val filtersState = stringResource(
        if (namesEnabled) Res.string.search_filters_names_on else Res.string.search_filters_default,
    )
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            BadgedBox(
                badge = {
                    if (namesEnabled && !expanded) {
                        Badge(
                            modifier = Modifier
                                .testTag(SEARCH_FILTERS_BADGE_TAG),
                        )
                    }
                },
            ) {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { setExpanded(it) },
                    modifier = Modifier
                        .then(if (isCoachMarkTarget) Modifier.coachMarkTarget(CoachTarget.Filters) else Modifier)
                        .semantics {
                            stateDescription = filtersState
                        },
                    containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                        initialColor = MaterialTheme.colorScheme.secondaryContainer,
                        finalColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    val iconColor = ToggleFloatingActionButtonDefaults.iconColor(
                        initialColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        finalColor = MaterialTheme.colorScheme.onSecondary,
                    )
                    Icon(
                        imageVector = if (checkedProgress > 0.5f) Icons.Filled.Close else TuneIcon,
                        contentDescription = label,
                        modifier = Modifier
                            .animateIcon(
                                checkedProgress = { checkedProgress },
                                color = iconColor,
                            ),
                    )
                }
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            // The tap asks for the opposite of what is stored and leaves
            // the menu open: closing it here would turn setting two
            // filters into four taps.
            onClick = { onNamesEnabledChange(!namesEnabled) },
            text = {
                Text(text = stringResource(Res.string.search_names_toggle))
            },
            icon = {
                if (namesEnabled) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                    )
                }
            },
            // A real switch to accessibility, driven by the stored value:
            // a pill whose colour is the only clue reports nothing, and
            // a test could not tell one stuck "off" beside names that
            // are on.
            modifier = Modifier
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(namesEnabled)
                },
            containerColor = if (namesEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (namesEnabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

/**
 * Material's Outlined "Tune" icon. It lives in `material-icons-extended`,
 * which this project does not depend on — `material-icons-core` is the
 * only icon set — so its path is copied here (Apache 2.0, as the icon
 * set itself) rather than adding the whole extended set for one glyph.
 */
private val TuneIcon: ImageVector by lazy {
    materialIcon(name = "Outlined.Tune") {
        materialPath {
            moveTo(3.0f, 17.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(6.0f)
            verticalLineToRelative(-2.0f)
            lineTo(3.0f, 17.0f)
            close()
            moveTo(3.0f, 5.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(10.0f)
            lineTo(13.0f, 5.0f)
            lineTo(3.0f, 5.0f)
            close()
            moveTo(13.0f, 21.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(8.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(-8.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(6.0f)
            horizontalLineToRelative(2.0f)
            close()
            moveTo(7.0f, 9.0f)
            verticalLineToRelative(2.0f)
            lineTo(3.0f, 11.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(4.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(2.0f)
            lineTo(9.0f, 9.0f)
            lineTo(7.0f, 9.0f)
            close()
            moveTo(21.0f, 13.0f)
            verticalLineToRelative(-2.0f)
            lineTo(11.0f, 11.0f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(10.0f)
            close()
            moveTo(15.0f, 9.0f)
            horizontalLineToRelative(2.0f)
            lineTo(17.0f, 7.0f)
            horizontalLineToRelative(4.0f)
            lineTo(21.0f, 5.0f)
            horizontalLineToRelative(-4.0f)
            lineTo(17.0f, 3.0f)
            horizontalLineToRelative(-2.0f)
            verticalLineToRelative(6.0f)
            close()
        }
    }
}
