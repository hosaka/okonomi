package cc.hosaka.okonomi.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Which top-level section is shown and how system back moves
 * between them: back on a non-default section returns to the
 * default one, back on the default section is left to the platform.
 */
@Stable
class HomeSelectionState(
    val items: List<HomeNavigationItem>,
    val default: HomeNavigationItem = items.first(),
    initialKey: String = default.key,
) {
    var selectedKey: String by mutableStateOf(initialKey)
        private set

    // Replay-safe reselect signal: sections that are not composed while
    // off screen read the counter when they come back and compare it to
    // the last value they handled, so no reselect event is ever lost to
    // a one-shot emission.
    private val reselections = mutableStateMapOf<String, Int>()

    val selected: HomeNavigationItem
        get() = items.firstOrNull { it.key == selectedKey } ?: default

    val isBackEnabled: Boolean
        get() = selected.key != default.key

    /** How often [select] hit the already-selected [key] so far. */
    fun reselectionsOf(key: String): Int = reselections[key] ?: 0

    /** Switches to [item]; a no-op when it is already selected. */
    fun switchTo(item: HomeNavigationItem) {
        selectedKey = item.key
    }

    /**
     * Records that the already-active [item] was tapped at its root,
     * so the section's screen can react (focus its search field).
     */
    fun signalReselect(item: HomeNavigationItem) {
        reselections[item.key] = reselectionsOf(item.key) + 1
    }

    fun onBack(): Boolean {
        if (!isBackEnabled) {
            return false
        }
        selectedKey = default.key
        return true
    }

    companion object {
        fun saver(
            items: List<HomeNavigationItem>,
            default: HomeNavigationItem,
        ): Saver<HomeSelectionState, String> = Saver(
            save = { it.selectedKey },
            restore = { key ->
                HomeSelectionState(
                    items = items,
                    default = default,
                    initialKey = key,
                )
            },
        )
    }
}

/**
 * How often the currently shown section was reselected while already
 * active. Screens compare the value against the last one they handled
 * (e.g. to focus their search field) instead of treating it as an
 * event stream; see [resolveHomeReselect] for the comparison rule.
 */
val LocalHomeReselect = compositionLocalOf { 0 }

/**
 * What tapping a navigation item does.
 */
enum class HomeSelectAction {
    /** A different section was tapped: switch to it. */
    Switch,

    /**
     * The active section was tapped while showing a pushed screen:
     * pop its stack back to the root. The tap is consumed by the pop,
     * so no reselect is signalled — the section root must not steal
     * focus on the same tap that revealed it.
     */
    PopToRoot,

    /**
     * The active section was tapped while already at its root: signal
     * the reselect so the screen can act on it (the search field
     * focuses and raises the IME).
     */
    SignalReselect,
}

/**
 * Pure decision rule for a navigation item tap. [depth] is the size of
 * the tapped section's own back stack.
 */
fun homeSelectAction(
    tappedKey: String,
    selectedKey: String,
    depth: Int,
): HomeSelectAction = when {
    tappedKey != selectedKey -> HomeSelectAction.Switch
    isAtRoot(depth) -> HomeSelectAction.SignalReselect
    else -> HomeSelectAction.PopToRoot
}

/** A section showing only its root route has nothing to pop. */
fun isAtRoot(depth: Int): Boolean = depth <= 1

/**
 * How many entries a [HomeSelectAction.PopToRoot] must drop from a
 * back stack of [depth] to leave the root route standing. Zero when
 * there is nothing above the root — popping one entry per tap would
 * leave the user mid-stack instead of at the top of the section.
 */
fun popToRootCount(depth: Int): Int = (depth - 1).coerceAtLeast(0)

/**
 * What a screen decided to do with an observed reselect counter value.
 */
data class HomeReselectDecision(
    val handled: Int,
    val focus: Boolean,
)

/**
 * Pure decision rule for the replay-safe reselect counter. [handled] is
 * the last counter value the screen acted on, or null when the screen
 * has no memory of one yet:
 *
 * - (Re)entering composition (`handled == null`): sync to [current]
 *   without focusing. A tap that uncovers the screen never increments
 *   the counter ([HomeSelectAction.PopToRoot] consumes it), so there is
 *   nothing to replay here — only a counter that moved on without this
 *   screen, which must not focus retroactively.
 * - Increment observed while composed: focus.
 * - Counter behind the handled value (process-death restore reset the
 *   counter): resync quietly, never a spurious focus.
 */
fun resolveHomeReselect(current: Int, handled: Int?): HomeReselectDecision = when {
    handled == null -> HomeReselectDecision(handled = current, focus = false)
    current > handled -> HomeReselectDecision(handled = current, focus = true)
    current < handled -> HomeReselectDecision(handled = current, focus = false)
    else -> HomeReselectDecision(handled = handled, focus = false)
}

@Composable
fun rememberHomeSelectionState(
    items: List<HomeNavigationItem>,
    default: HomeNavigationItem = items.first(),
): HomeSelectionState = rememberSaveable(
    items,
    default,
    saver = HomeSelectionState.saver(items, default),
) {
    HomeSelectionState(
        items = items,
        default = default,
    )
}
