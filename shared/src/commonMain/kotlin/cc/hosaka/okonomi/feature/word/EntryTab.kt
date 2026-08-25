package cc.hosaka.okonomi.feature.word

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.ui.graphics.vector.ImageVector
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_tab_forms
import okonomi.shared.generated.resources.entry_tab_kanji
import okonomi.shared.generated.resources.entry_tab_phrases
import okonomi.shared.generated.resources.entry_tab_word
import org.jetbrains.compose.resources.StringResource

/** The tabs of the entry view, in swipe order. */
enum class EntryTab(
    val label: StringResource,
    val icon: ImageVector,
) {
    Word(Res.string.entry_tab_word, Icons.Outlined.Info),
    Kanji(Res.string.entry_tab_kanji, Icons.Outlined.Create),
    Forms(Res.string.entry_tab_forms, Icons.AutoMirrored.Outlined.List),
    Phrases(Res.string.entry_tab_phrases, Icons.Outlined.MailOutline),
}
