package cc.hosaka.okonomi.feature.kanji

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_kanji_error
import okonomi.shared.generated.resources.entry_kanji_none
import okonomi.shared.generated.resources.entry_retry
import org.jetbrains.compose.resources.stringResource

/**
 * The characters of the headword, one card each. The tab loads its own
 * data rather than riding on the entry state: a kanjidic failure must
 * cost this tab alone, never the entry view or its other tabs.
 */
@Composable
fun KanjiTab(
    entry: EntryDetail,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state = produceKanjiTabState(
        entryId = entry.entryId,
        headword = entry.headword,
    )
    KanjiTabContent(
        state = state.value,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun KanjiTabContent(
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

@Composable
private fun KanjiList(
    characters: List<KanjiCharacter>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(
            items = characters,
            key = { it.literal },
        ) { character ->
            KanjiCard(
                character = character,
                modifier = Modifier
                    .padding(
                        horizontal = Dimens.contentPadding,
                        vertical = Dimens.verticalPaddingHalf,
                    ),
            )
        }
    }
}
