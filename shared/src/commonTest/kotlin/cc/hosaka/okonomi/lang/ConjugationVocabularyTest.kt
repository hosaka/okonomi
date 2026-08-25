package cc.hosaka.okonomi.lang

import cc.hosaka.okonomi.deinflect.UNCONJUGATED_POS_CODES
import cc.hosaka.okonomi.deinflect.posCodesToConditionFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two-way guard between the conjugator's paradigm table and the
 * deinflector's condition-flag table. They classify the same JMdict
 * codes from opposite directions, and a code that falls out of one but
 * not the other is silent in both: a paradigm with no flags can never
 * be round-trip verified, and flags with no paradigm are a tab that has
 * nothing to say about a word it should.
 *
 * The third test is the one that catches JMdict moving underneath us: a
 * code added upstream shows up here as a red test rather than as an
 * entry whose Forms tab quietly renders the empty state.
 */
class ConjugationVocabularyTest {

    /**
     * The shipped codes with no conjugation paradigm, and why. Nouns,
     * particles, counters and the like have nothing to inflect; the
     * archaic classes (`v2*`, `v4*`, `vn`, `vr`) have no rules in the
     * ported deinflector either, so they carry no flags and could not
     * be verified even if they had paradigms; `vi`/`vt`/`aux`/`aux-v`
     * qualify a verb rather than classify it; and `vs` and `v-unspec`
     * are the two that do carry flags on purpose — see
     * [UNCONJUGATED_POS_CODES].
     */
    private val deliberatelyNotConjugated = setOf(
        "adj-f", "adj-ku", "adj-na", "adj-nari", "adj-no", "adj-pn",
        "adj-shiku", "adj-t", "adv", "adv-to", "aux", "aux-v",
        "conj", "cop", "ctr", "exp", "int", "n",
        "n-pref", "n-suf", "num", "pn", "pref", "prt",
        "suf", "unc", "v-unspec", "v2a-s", "v2b-k", "v2d-s",
        "v2g-k", "v2g-s", "v2h-k", "v2h-s", "v2k-k", "v2k-s",
        "v2m-s", "v2n-s", "v2r-k", "v2r-s", "v2s-s", "v2t-k",
        "v2t-s", "v2w-s", "v2y-k", "v2y-s", "v2z-s", "v4b",
        "v4g", "v4h", "v4k", "v4m", "v4r", "v4s",
        "v4t", "vi", "vn", "vr", "vs", "vt",
    )

    @Test
    fun `every code with a paradigm carries condition flags`() {
        for (code in conjugablePosCodes) {
            assertNotEquals(
                0L,
                posCodesToConditionFlags(code),
                "$code has a paradigm but no condition flags, so nothing it produces can be verified",
            )
        }
    }

    @Test
    fun `every code with condition flags has a paradigm or is a named exception`() {
        val unexplained = shippedPosCodes.filter { code ->
            posCodesToConditionFlags(code) != 0L &&
                code !in conjugablePosCodes &&
                code !in UNCONJUGATED_POS_CODES
        }
        assertEquals(emptyList(), unexplained, "these codes name a verb class the Forms tab does not conjugate")

        // The named exceptions have to be exactly that: flagged, and
        // deliberately without a paradigm.
        for (code in UNCONJUGATED_POS_CODES) {
            assertNotEquals(0L, posCodesToConditionFlags(code), "$code is listed as flagged but is not")
            assertTrue(code !in conjugablePosCodes, "$code is listed as unconjugated but has a paradigm")
        }
    }

    @Test
    fun `every part of speech in the shipped dictionary is handled or explicitly ignored`() {
        val unaccounted = shippedPosCodes.filter {
            it !in conjugablePosCodes && it !in deliberatelyNotConjugated
        }
        assertEquals(
            emptyList(),
            unaccounted,
            "JMdict has a part of speech this increment never considered; decide on a paradigm or list it as ignored",
        )
        // And nothing in the ignore list may quietly grow a paradigm.
        assertEquals(emptySet(), conjugablePosCodes intersect deliberatelyNotConjugated)
    }

    /**
     * `v5uru` is the one paradigm with no sense in the shipped
     * dictionary. It stays because JMdict still defines the code, and
     * pinning it here records that its absence is the data's doing
     * rather than an oversight.
     */
    @Test
    fun `the uru class has a paradigm the shipped dictionary never uses`() {
        assertTrue("v5uru" in conjugablePosCodes)
        assertTrue("v5uru" !in shippedPosCodes)
        assertEquals(ConjugationClass.Ichidan, conjugationClassOf("v5uru", "得る"))
    }

    @Test
    fun `a code the conjugator does not know produces nothing rather than guessing`() {
        for (code in listOf("n", "adj-na", "v4r", "vs", "v-unspec", "", "not-a-code")) {
            assertNull(conjugationClassOf(code, "食べる"), code)
            assertEquals(emptyList(), conjugate("食べる", code), code)
        }
    }
}
