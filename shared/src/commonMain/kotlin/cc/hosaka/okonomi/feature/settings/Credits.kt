package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Immutable
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.settings_credit_usage_jmdict
import okonomi.shared.generated.resources.settings_credit_usage_jmnedict
import okonomi.shared.generated.resources.settings_credit_usage_kanjidic
import okonomi.shared.generated.resources.settings_credit_usage_radkfile
import okonomi.shared.generated.resources.settings_credit_usage_yomitan
import okonomi.shared.generated.resources.settings_credits_edrdg_statement
import org.jetbrains.compose.resources.StringResource

/**
 * One attributed upstream source. The credits UI renders this manifest,
 * never hardcoded prose, so crediting a new source (say Tatoeba) is a
 * single new entry here plus its usage string.
 */
@Immutable
data class CreditEntry(
    val name: String,
    val licence: String,
    val licenceUrl: String,
    val usage: StringResource,
    /**
     * Non-translatable technical note rendered alongside the usage text,
     * such as a pinned upstream commit. Kept out of the translatable
     * usage string on purpose.
     */
    val detail: String? = null,
)

const val EDRDG_LICENCE_URL = "https://www.edrdg.org/edrdg/licence.html"

private const val EDRDG_LICENCE_NAME = "EDRDG licence"

/**
 * The statement the EDRDG licence requires distributed products to
 * display, rendered above the credit entries together with a link to
 * [EDRDG_LICENCE_URL].
 */
val edrdgStatement: StringResource = Res.string.settings_credits_edrdg_statement

val creditEntries: List<CreditEntry> = listOf(
    CreditEntry(
        name = "JMdict",
        licence = EDRDG_LICENCE_NAME,
        licenceUrl = EDRDG_LICENCE_URL,
        usage = Res.string.settings_credit_usage_jmdict,
    ),
    CreditEntry(
        name = "JMnedict",
        licence = EDRDG_LICENCE_NAME,
        licenceUrl = EDRDG_LICENCE_URL,
        usage = Res.string.settings_credit_usage_jmnedict,
    ),
    CreditEntry(
        name = "KANJIDIC2",
        licence = EDRDG_LICENCE_NAME,
        licenceUrl = EDRDG_LICENCE_URL,
        usage = Res.string.settings_credit_usage_kanjidic,
    ),
    CreditEntry(
        name = "RADKFILE",
        licence = EDRDG_LICENCE_NAME,
        licenceUrl = EDRDG_LICENCE_URL,
        usage = Res.string.settings_credit_usage_radkfile,
    ),
    CreditEntry(
        name = "Yomitan",
        licence = "GPL-3.0",
        licenceUrl = "https://github.com/yomidevs/yomitan",
        usage = Res.string.settings_credit_usage_yomitan,
    ),
)
