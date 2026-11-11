package cc.hosaka.okonomi.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.ui.ScaffoldColumn
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.dictionary_settings_back
import okonomi.shared.generated.resources.dictionary_settings_learning
import okonomi.shared.generated.resources.dictionary_settings_learning_english
import okonomi.shared.generated.resources.dictionary_settings_learning_japanese
import okonomi.shared.generated.resources.dictionary_settings_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun DictionarySettingsScreen(
    state: DictionarySettingsState,
) {
    val scrollBehavior = ToolbarBehavior.behavior()
    val navigation = LocalNavigationController.current
    ScaffoldColumn(
        topAppBarScrollBehavior = scrollBehavior,
        topBar = {
            LargeToolbar(
                title = {
                    Text(
                        text = stringResource(Res.string.dictionary_settings_title),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navigation.pop()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.dictionary_settings_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        // Nothing until the store answers, for the reason Appearance
        // holds its switches back: a choice drawn on a guess would jump
        // in front of a reader whose stored value differs.
        val learningLanguage = state.learningLanguage
        if (learningLanguage != null) {
            LearningLanguageRow(
                selected = learningLanguage,
                onSelect = state.onLearningLanguageChange,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * "Learning", then the two languages as one single-choice segmented
 * button, each named in its own language and in [LearningLanguage]'s
 * order: 日本語, then English.
 *
 * Tapping the language already chosen does nothing rather than writing
 * it again. Writing it would turn a device default the reader merely
 * looked at into a choice they never made, pinning it against a later
 * change of device language.
 */
@Composable
private fun LearningLanguageRow(
    selected: LearningLanguage,
    onSelect: ((LearningLanguage) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(
                horizontal = Dimens.horizontalPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.dictionary_settings_learning),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = Dimens.horizontalPadding),
        )
        val languages = LearningLanguage.entries
        SingleChoiceSegmentedButtonRow {
            languages.forEachIndexed { index, language ->
                SegmentedButton(
                    selected = language == selected,
                    onClick = {
                        if (language != selected) onSelect?.invoke(language)
                    },
                    enabled = onSelect != null,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size),
                    label = {
                        Text(text = languageName(language))
                    },
                )
            }
        }
    }
}

@Composable
private fun languageName(language: LearningLanguage): String = when (language) {
    LearningLanguage.Japanese -> stringResource(Res.string.dictionary_settings_learning_japanese)
    LearningLanguage.English -> stringResource(Res.string.dictionary_settings_learning_english)
}
