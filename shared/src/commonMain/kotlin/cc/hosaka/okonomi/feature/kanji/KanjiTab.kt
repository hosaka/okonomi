package cc.hosaka.okonomi.feature.kanji

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.radical.RadicalRoute
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_kanji_detail_open
import okonomi.shared.generated.resources.entry_kanji_error
import okonomi.shared.generated.resources.entry_kanji_none
import okonomi.shared.generated.resources.entry_retry
import org.jetbrains.compose.resources.stringResource

/**
 * The characters of the headword, one card each. The tab loads its own
 * data rather than riding on the entry state: a kanjidic failure must
 * cost this tab alone, never the entry view or its other tabs.
 *
 * [loadEnabled] false means the pager is only passing through this page
 * on its way somewhere else: the tab renders its loading body and asks
 * the database for nothing. The producer is not merely idle but
 * uncalled, so no query is started and nothing has to be cancelled. Its
 * view model outlives the gate closing again, so coming back to a tab
 * that already loaded is still instant.
 *
 * It has no default on purpose. A default of true would mean that
 * deleting the argument at the call site compiles and silently restores
 * the bug — and the pass-through it guards against only happens
 * mid-animation on a device, which no host test can stand at the right
 * frame to see. Making the caller say what it wants is the check that
 * does not depend on catching the moment.
 */
@Composable
fun KanjiTab(
    entry: EntryDetail,
    contentPadding: PaddingValues,
    loadEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = if (loadEnabled) {
        produceKanjiTabState(
            entryId = entry.entryId,
            headword = entry.headword,
        ).value
    } else {
        KanjiTabState()
    }
    KanjiTabContent(
        state = state,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

/** Internal so a UI test can drive a Ready list without a database. */
@Composable
internal fun KanjiTabContent(
    state: KanjiTabState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (val content = state.content) {
        KanjiTabContentState.Loading -> CenteredBox(
            modifier = modifier,
            contentPadding = contentPadding,
        ) {
            CircularProgressIndicator()
        }

        is KanjiTabContentState.Error -> CenteredMessage(
            text = stringResource(Res.string.entry_kanji_error),
            modifier = modifier,
            contentPadding = contentPadding,
            action = content.onRetry?.let { retry ->
                {
                    TextButton(onClick = retry) {
                        Text(text = stringResource(Res.string.entry_retry))
                    }
                }
            },
        )

        KanjiTabContentState.NoKanji -> CenteredMessage(
            text = stringResource(Res.string.entry_kanji_none),
            modifier = modifier,
            contentPadding = contentPadding,
        )

        is KanjiTabContentState.Ready -> KanjiList(
            characters = content.characters,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

/**
 * The cards, and the overlay one of them opens.
 *
 * The selection lives here rather than in `KanjiTabState`: which card
 * the reader tapped is ephemeral UI state that no producer needs to know
 * about, and putting it in the state would make a database load and a
 * tap the same kind of event.
 *
 * The dialog is a sibling of the list rather than a child of an item,
 * for two reasons that hold: `LazyListScope` is not a composable scope,
 * so a dialog cannot be declared there without wrapping it in an
 * `item`, and one overlay belongs to the list rather than one per card.
 *
 * Not for the reason usually given for this shape. An open `Dialog`
 * takes the touch input, so the list underneath cannot be scrolled out
 * from under it and the item could not be disposed that way — the
 * placement is right on its own terms, and stating a reason that does
 * not hold would invite someone to undo it once they found that out.
 */
@Composable
private fun KanjiList(
    characters: List<KanjiCharacter>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val detail = rememberKanjiDetailDialogState()
    val navigation = LocalNavigationController.current
    // Both halves of the tap, in order: the overlay has served its
    // purpose once the reader has chosen where to go, and leaving it
    // standing over the screen it just opened would be a second thing
    // to dismiss.
    //
    // A radical opens its own screen rather than seeding a word search.
    // The question a radical asks is "which kanji are built from this",
    // which a word search answers badly at best: 61 radicals carry no
    // JMdict entry at all and dead-end on "No results", and a radical
    // that is also a common word buries its kanji under hundreds of word
    // rows.
    val onRadicalClick: (String) -> Unit = remember(navigation, detail) {
        { radical ->
            detail.dismiss()
            navigation.navigate(RadicalRoute(radical))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        LazyColumn(
            // The caller's modifier belongs to the scrolling list, not
            // to the wrapper the dialog needed: a test tag or a nested
            // scroll connection meant for the list would otherwise land
            // on a Box that does neither.
            modifier = modifier
                .fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(
                items = characters,
                key = { it.literal },
            ) { character ->
                // A character with neither nanori nor radicals would have
                // nothing to put in an overlay, so it offers no way to
                // open one.
                val open: (() -> Unit)? = remember(character, detail) {
                    if (character.hasDetailToShow) {
                        { detail.show(character) }
                    } else {
                        null
                    }
                }
                // Spacing belongs to ListCard, so the row does not set its own.
                KanjiCard(
                    character = character,
                    onClick = open,
                    // Per character, not hoisted: the label names the
                    // kanji it belongs to, so the spoken action hint
                    // identifies its own card in a list of them.
                    onClickLabel = stringResource(
                        Res.string.entry_kanji_detail_open,
                        character.literal,
                    ),
                )
            }
        }
        KanjiDetailDialog(
            state = detail,
            onRadicalClick = onRadicalClick,
        )
    }
}
