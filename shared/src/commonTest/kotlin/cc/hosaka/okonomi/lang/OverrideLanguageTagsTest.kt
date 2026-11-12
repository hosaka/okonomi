package cc.hosaka.okonomi.lang

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The language list a UI language override applies, on both platforms:
 * the chosen language first, and the phone's own languages kept behind
 * it, since text with no language of its own takes its kanji style from
 * the first CJK language in the list.
 */
class OverrideLanguageTagsTest {

    @Test
    fun `a language the phone does not list goes first as a bare code`() {
        assertEquals(listOf("ja", "en-US"), overrideLanguageTags("ja", listOf("en-US")))
    }

    /** A Japanese phone showing English keeps Japanese for its kanji, and en-GB keeps its formats. */
    @Test
    fun `a language the phone lists goes first as the phone writes it and is not repeated`() {
        assertEquals(
            listOf("en-GB", "ja-JP", "fr-FR"),
            overrideLanguageTags("en", listOf("ja-JP", "en-GB", "fr-FR")),
        )
    }
}
