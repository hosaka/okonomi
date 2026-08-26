package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Immutable
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.settings_credit_usage_furiganable
import okonomi.shared.generated.resources.settings_credit_usage_jmdict
import okonomi.shared.generated.resources.settings_credit_usage_jmnedict
import okonomi.shared.generated.resources.settings_credit_usage_kanjidic
import okonomi.shared.generated.resources.settings_credit_usage_radkfile
import okonomi.shared.generated.resources.settings_credit_usage_tatoeba
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
 * Unqualified on purpose. Tatoeba states that its data is released
 * "under various Creative Commons licenses" — plural, and deliberately
 * unspecific about which one covers a given sentence — so naming a
 * particular variant here would assert a precision the source itself
 * declines to give. Attribution is the obligation these entries
 * discharge, and the terms page is where the reader goes for the rest.
 */
private const val CREATIVE_COMMONS_LICENCE_NAME = "Creative Commons"

/** Where the terms these sentences reach us under are stated. */
private const val TATOEBA_TERMS_URL = "https://tatoeba.org/en/terms_of_use"

private const val APACHE_2_LICENCE_NAME = "Apache-2.0"

private const val APACHE_2_LICENCE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"

/**
 * Where the furigana renderer came from, as a reader of the Settings
 * screen wants it: the project and the version, and nothing about our
 * build. The commit it was taken at belongs to whoever maintains the
 * copy, and is recorded in the headers of the vendored sources
 * themselves (`ui/furigana/`), not here.
 */
private const val FURIGANABLE_SOURCE = "github.com/turtlekazu/Furiganable (v0.3.1)"

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
    CreditEntry(
        name = "Tatoeba (Tanaka Corpus)",
        licence = CREATIVE_COMMONS_LICENCE_NAME,
        licenceUrl = TATOEBA_TERMS_URL,
        usage = Res.string.settings_credit_usage_tatoeba,
    ),
    CreditEntry(
        name = "Furiganable",
        licence = APACHE_2_LICENCE_NAME,
        // The licence text itself, not the repository: the repository
        // carries no LICENSE file at all. Apache-2.0 is stated only in
        // its gradle.properties, as POM_LICENSE_NAME/POM_LICENSE_URL,
        // which is what its published artifacts declare and therefore
        // what this copy is used under.
        licenceUrl = APACHE_2_LICENCE_URL,
        usage = Res.string.settings_credit_usage_furiganable,
        detail = FURIGANABLE_SOURCE,
    ),
)
