package cc.hosaka.okonomi.feature.favourites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.anki.ANKI_DECK_NAME
import cc.hosaka.okonomi.anki.AnkiSendResult
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.search.SearchResultRow
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
import cc.hosaka.okonomi.ui.OverflowMenu
import cc.hosaka.okonomi.ui.plusScreenPadding
import cc.hosaka.okonomi.ui.scrollIndicator
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import cc.hosaka.okonomi.user.UserList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.anki_dismiss
import okonomi.shared.generated.resources.anki_failed_message
import okonomi.shared.generated.resources.anki_failed_partly_message
import okonomi.shared.generated.resources.anki_failed_title
import okonomi.shared.generated.resources.anki_no_collection_message
import okonomi.shared.generated.resources.anki_no_collection_title
import okonomi.shared.generated.resources.anki_nothing_new_message
import okonomi.shared.generated.resources.anki_nothing_new_title
import okonomi.shared.generated.resources.anki_permission_message
import okonomi.shared.generated.resources.anki_permission_title
import okonomi.shared.generated.resources.anki_sent_already_there
import okonomi.shared.generated.resources.anki_sent_new_words
import okonomi.shared.generated.resources.anki_sent_title
import okonomi.shared.generated.resources.anki_sent_words
import okonomi.shared.generated.resources.anki_unavailable_message
import okonomi.shared.generated.resources.anki_unavailable_title
import okonomi.shared.generated.resources.favourites_clear_cancel
import okonomi.shared.generated.resources.favourites_clear_confirm
import okonomi.shared.generated.resources.favourites_clear_message
import okonomi.shared.generated.resources.favourites_clear_title
import okonomi.shared.generated.resources.favourites_empty
import okonomi.shared.generated.resources.favourites_error
import okonomi.shared.generated.resources.favourites_import_replace_cancel
import okonomi.shared.generated.resources.favourites_import_replace_confirm
import okonomi.shared.generated.resources.favourites_import_replace_message
import okonomi.shared.generated.resources.favourites_import_replace_title
import okonomi.shared.generated.resources.favourites_import_unreadable_dismiss
import okonomi.shared.generated.resources.favourites_import_unreadable_message
import okonomi.shared.generated.resources.favourites_import_unreadable_title
import okonomi.shared.generated.resources.favourites_remove
import okonomi.shared.generated.resources.favourites_retry
import okonomi.shared.generated.resources.favourites_switch_list
import okonomi.shared.generated.resources.favourites_title
import okonomi.shared.generated.resources.history_empty
import okonomi.shared.generated.resources.history_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * How long a swiped-away row waits to be taken out of the list before it
 * comes back. The removal is a queued write the screen hears about only
 * by the row leaving the list; one that failed sends nothing back, so a
 * row still here after this long is a write that did not land, and
 * leaving it swiped off would show a removal that never happened. A
 * write that lands takes the row with it long before this.
 */
private val SWIPED_ROW_RETURN_DELAY = 1.5.seconds

/** Material's minimum touch target, for the list picker in the title. */
private val TITLE_MIN_TOUCH_HEIGHT = 48.dp

/**
 * The Favourites tab: the entries the reader saved, newest first, drawn
 * as the rows a search draws.
 *
 * Same rows on purpose. A saved word is the same word it was in the
 * results list — headword with its reading over the kanji, senses under
 * it — and giving it a second presentation would only make the two
 * screens disagree about what a word looks like.
 *
 * Two lists share it, Favourites and History, picked from the toolbar
 * title. The bottom bar's tab keeps saying "Favourites"; the title says
 * which list is on show.
 *
 * A pure renderer, and deliberately still one now that it has an export
 * and an import on it: [onExportClick] and [onImportClick] are opaque
 * taps, and the dialogs are read out of [FavouritesState]. The file
 * dialogs themselves live in `FavouritesRoute`, so nothing here has to
 * be hosted by anything a UI test cannot build.
 */
@Composable
fun FavouritesScreen(
    state: FavouritesState,
    onExportClick: (() -> Unit)?,
    onImportClick: (() -> Unit)?,
) {
    // Hoisted so the toolbar can ask whether this list actually
    // scrolls. One saved word does not fill the screen, and a toolbar
    // that collapses over content that cannot move leaves a band of
    // empty space where it used to be.
    val listState = rememberLazyListState()
    val scrollBehavior = ToolbarBehavior.behavior(
        canScroll = { listState.canScrollForward || listState.canScrollBackward },
    )
    // Switching lists is a new list of rows, not a movement within one:
    // the new list starts at its top, with the toolbar expanded again so
    // its title is not entered collapsed to a strip.
    //
    // Only on an actual switch, from one known list to another. The
    // effect also runs on the first composition — coming back to the
    // tab — where the scroll position was just restored and must be
    // kept, so the first list seen is adopted rather than reset to. The
    // seeded frame names no list at all (see FavouritesState.list) and
    // is skipped, so a tab coming back to History is not mistaken for a
    // switch away from a Favourites that was never on show.
    //
    // The toolbar half of this — the offsets set back to zero — is
    // asserted by FavouritesScreenUiTest through where the first row
    // lands, which is the toolbar's height as the content padding.
    var shownList by remember { mutableStateOf<UserList?>(null) }
    LaunchedEffect(state.list) {
        val current = state.list ?: return@LaunchedEffect
        val previous = shownList
        shownList = current
        if (previous == null || previous == current) return@LaunchedEffect
        listState.scrollToItem(0)
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }
    // The list owns the scroll, so the plain Scaffold is used and its
    // inner padding goes to the list as content padding, letting the
    // rows scroll under the collapsing toolbar.
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeToolbar(
                // The menu rides in the title row rather than in the
                // toolbar's `actions` slot, which is the one place this
                // screen departs from Settings and Libraries.
                //
                // `actions` is pinned to the top row, so while the bar
                // is expanded the button floats in the corner with the
                // title sitting well below it, belonging to nothing. In
                // here it sits on the title's own line and rises with it
                // as the bar collapses, which is one movement instead of
                // two. The icon is not text, so the title's typography
                // shrinking on collapse does not take the icon with it.
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FavouritesTitle(
                                list = state.list,
                                onSelectList = state.onSelectList,
                            )
                        }
                        FavouritesOverflowMenu(
                            onExportClick = onExportClick,
                            onImportClick = onImportClick,
                            onClearClick = state.onClearList,
                            showSendToAnki = state.showSendToAnki,
                            onSendToAnkiClick = state.onSendToAnki,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        val contentPadding = innerPadding.plusScreenPadding()
        when (val content = state.content) {
            FavouritesContentState.Loading -> CenteredBox(
                contentPadding = contentPadding,
            ) {
                CircularProgressIndicator()
            }

            is FavouritesContentState.Error -> CenteredMessage(
                text = stringResource(Res.string.favourites_error),
                contentPadding = contentPadding,
                action = content.onRetry?.let { retry ->
                    {
                        TextButton(onClick = retry) {
                            Text(text = stringResource(Res.string.favourites_retry))
                        }
                    }
                },
            )

            is FavouritesContentState.Ready -> if (content.hits.isEmpty()) {
                CenteredMessage(
                    text = stringResource(
                        when (state.list) {
                            UserList.History -> Res.string.history_empty
                            UserList.Favourites, null -> Res.string.favourites_empty
                        },
                    ),
                    contentPadding = contentPadding,
                )
            } else {
                FavouritesList(
                    hits = content.hits,
                    contentPadding = contentPadding,
                    listState = listState,
                    removeLabel = stringResource(
                        Res.string.favourites_remove,
                        state.list?.let { stringResource(it.title) }.orEmpty(),
                    ),
                    onRemoveEntry = state.onRemoveEntry,
                )
            }
        }
    }
    FavouritesImportDialog(state.importPrompt)
    FavouritesClearDialog(state.clearPrompt)
    FavouritesAnkiDialog(state.ankiPrompt)
}

/** What the toolbar calls each list. */
private val UserList.title: StringResource
    get() = when (this) {
        UserList.Favourites -> Res.string.favourites_title
        UserList.History -> Res.string.history_title
    }

/**
 * The toolbar title as the list picker: the list on show, with a
 * dropdown affordance, opening the app's one menu panel with every list
 * in it and the current one checked. With nothing to pick
 * ([onSelectList] null, the seeded first frame) it is the plain title.
 *
 * The check is a decoration; what says which row is current is the
 * row's `selected` semantics, so a test — or a screen reader — can tell
 * a check on the wrong row from one on the right row.
 */
@Composable
private fun FavouritesTitle(
    list: UserList?,
    onSelectList: ((UserList) -> Unit)?,
) {
    // Nothing rather than a guess on the seeded frame; see
    // FavouritesState.list.
    val title = list?.let { stringResource(it.title) }.orEmpty()
    if (list == null || onSelectList == null) {
        Text(text = title)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            // A dropdown, announced as one, and never a thinner target
            // than the minimum touch height however small the title's
            // type gets as the toolbar collapses.
            modifier = Modifier
                .heightIn(min = TITLE_MIN_TOUCH_HEIGHT)
                .clickable(
                    onClickLabel = stringResource(Res.string.favourites_switch_list),
                    role = Role.DropdownList,
                ) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title)
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                // The row carries the action label; a description here
                // would announce the affordance twice.
                contentDescription = null,
            )
        }
        OverflowMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            UserList.entries.forEach { option ->
                val current = option == list
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = current },
                    text = { Text(text = stringResource(option.title)) },
                    trailingIcon = if (current) {
                        { Icon(imageVector = Icons.Outlined.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelectList(option)
                    },
                )
            }
        }
    }
}

/**
 * Clear list's confirmation. It names the list, because the two menus
 * it is reached from look alike and only one list is being emptied.
 */
@Composable
private fun FavouritesClearDialog(
    prompt: FavouritesClearPrompt?,
) {
    if (prompt == null) return
    val name = stringResource(prompt.list.title)
    AlertDialog(
        onDismissRequest = prompt.onCancel,
        title = { Text(text = stringResource(Res.string.favourites_clear_title, name)) },
        text = { Text(text = stringResource(Res.string.favourites_clear_message, name)) },
        confirmButton = {
            TextButton(onClick = prompt.onConfirm) {
                Text(text = stringResource(Res.string.favourites_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = prompt.onCancel) {
                Text(text = stringResource(Res.string.favourites_clear_cancel))
            }
        },
    )
}

/**
 * How a send to AnkiDroid ended: one dialog for every outcome, because
 * a send leaves nothing on this screen to show for itself.
 */
@Composable
private fun FavouritesAnkiDialog(
    prompt: FavouritesAnkiPrompt?,
) {
    if (prompt == null) return
    val title: String
    val message: String
    when (val result = prompt.result) {
        is AnkiSendResult.Sent -> {
            title = stringResource(Res.string.anki_sent_title)
            message = if (result.alreadyThere == 0) {
                pluralStringResource(Res.plurals.anki_sent_words, result.added, result.added, ANKI_DECK_NAME)
            } else {
                pluralStringResource(Res.plurals.anki_sent_new_words, result.added, result.added, ANKI_DECK_NAME) +
                    " " +
                    pluralStringResource(Res.plurals.anki_sent_already_there, result.alreadyThere, result.alreadyThere)
            }
        }

        AnkiSendResult.NothingNew -> {
            title = stringResource(Res.string.anki_nothing_new_title)
            message = stringResource(Res.string.anki_nothing_new_message)
        }

        AnkiSendResult.Unavailable -> {
            title = stringResource(Res.string.anki_unavailable_title)
            message = stringResource(Res.string.anki_unavailable_message)
        }

        AnkiSendResult.PermissionDenied -> {
            title = stringResource(Res.string.anki_permission_title)
            message = stringResource(Res.string.anki_permission_message)
        }

        AnkiSendResult.NoCollection -> {
            title = stringResource(Res.string.anki_no_collection_title)
            message = stringResource(Res.string.anki_no_collection_message)
        }

        is AnkiSendResult.Failed -> {
            title = stringResource(Res.string.anki_failed_title)
            message = if (result.added == 0) {
                stringResource(Res.string.anki_failed_message)
            } else {
                pluralStringResource(
                    Res.plurals.anki_failed_partly_message,
                    result.attempted,
                    result.added,
                    result.attempted,
                    ANKI_DECK_NAME,
                )
            }
        }
    }
    AlertDialog(
        onDismissRequest = prompt.onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = prompt.onDismiss) {
                Text(text = stringResource(Res.string.anki_dismiss))
            }
        },
    )
}

/**
 * The app's only dialog. It exists because an import destroys what is
 * already saved and there is no undo, and because a file that cannot be
 * read has to say so somewhere: this app has no snackbar or toast, and
 * this feature is not the place to introduce one.
 */
@Composable
private fun FavouritesImportDialog(
    prompt: FavouritesImportPrompt?,
) {
    when (prompt) {
        null -> Unit

        is FavouritesImportPrompt.ConfirmOverwrite -> AlertDialog(
            onDismissRequest = prompt.onCancel,
            title = {
                Text(
                    text = stringResource(
                        Res.string.favourites_import_replace_title,
                        stringResource(prompt.list.title),
                    ),
                )
            },
            text = {
                Text(
                    text = stringResource(
                        Res.string.favourites_import_replace_message,
                        stringResource(prompt.list.title),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = prompt.onConfirm) {
                    Text(text = stringResource(Res.string.favourites_import_replace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = prompt.onCancel) {
                    Text(text = stringResource(Res.string.favourites_import_replace_cancel))
                }
            },
        )

        is FavouritesImportPrompt.Unreadable -> AlertDialog(
            onDismissRequest = prompt.onDismiss,
            title = { Text(text = stringResource(Res.string.favourites_import_unreadable_title)) },
            text = {
                Text(
                    text = stringResource(
                        Res.string.favourites_import_unreadable_message,
                        stringResource(prompt.list.title),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = prompt.onDismiss) {
                    Text(text = stringResource(Res.string.favourites_import_unreadable_dismiss))
                }
            },
        )
    }
}

@Composable
private fun FavouritesList(
    hits: List<SearchHit>,
    contentPadding: PaddingValues,
    listState: LazyListState,
    removeLabel: String,
    onRemoveEntry: ((Long) -> Unit)?,
) {
    val navigation = LocalNavigationController.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollIndicator(listState, contentPadding),
        state = listState,
        contentPadding = contentPadding,
    ) {
        items(
            items = hits,
            key = { it.entryId },
        ) { hit ->
            val row = @Composable {
                SearchResultRow(
                    hit = hit,
                    // No query behind this list, so nothing in a sense
                    // line is a match to light up.
                    glossTokens = emptyList(),
                    // Opening from here records nothing: History is what
                    // was opened from search, not from History itself.
                    // That holds because this screen has no store to
                    // record into, not because anything checks it — no
                    // host test can watch a recording that has no path.
                    onClick = {
                        navigation.navigate(EntryRoute(hit.entryId))
                    },
                )
            }
            if (onRemoveEntry == null) {
                row()
            } else {
                SwipeToRemove(
                    removeLabel = removeLabel,
                    onRemove = { onRemoveEntry(hit.entryId) },
                ) { row() }
            }
        }
    }
}

/**
 * A row that can be swiped away from end to start, and only that way,
 * out of the list on show and no other.
 *
 * The same removal is offered as an accessibility action, because a
 * swipe is the one gesture a screen reader cannot be expected to make.
 *
 * A removal plays one confirm haptic, from [remove], which both paths
 * go through. It fires on the dismissal itself — `onDismiss`, which a
 * swipe that springs back never reaches — not while the row is being
 * dragged. The accessibility action plays it too, on purpose: the
 * haptic says "a row was removed", not "the finger crossed a line", and
 * a reader removing by action deserves the same answer as one removing
 * by swipe. One function for both is what keeps that to exactly one per
 * removal on either path.
 *
 * There is no undo — the app has no snackbar — and no confirmation: a
 * History word comes back by opening it again, a Favourite by its heart.
 */
@Composable
private fun SwipeToRemove(
    removeLabel: String,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val remove = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onRemove()
    }
    val dismissState = rememberSwipeToDismissBoxState()
    // See SWIPED_ROW_RETURN_DELAY. Cancelled with the row when the
    // removal lands, which is the ordinary case.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) return@LaunchedEffect
        delay(SWIPED_ROW_RETURN_DELAY)
        dismissState.reset()
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(removeLabel) {
                    remove()
                    true
                },
            )
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { remove() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Dimens.contentPadding,
                        vertical = Dimens.verticalPaddingHalf,
                    )
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.large,
                    )
                    .padding(horizontal = Dimens.contentPadding),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    // The row's accessibility action says what this does.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}
