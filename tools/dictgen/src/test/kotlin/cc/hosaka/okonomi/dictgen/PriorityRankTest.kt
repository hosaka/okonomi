package cc.hosaka.okonomi.dictgen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PriorityRankTest {

    @Test
    fun tierOneTagsOutrankTheRest() {
        assertEquals(150L, PriorityRank.rank(listOf("ichi1")))
        assertEquals(150L, PriorityRank.rank(listOf("spec1")))
        assertEquals(150L, PriorityRank.rank(listOf("gai1")))
        assertEquals(250L, PriorityRank.rank(listOf("news1")))
        assertEquals(350L, PriorityRank.rank(listOf("news2")))
        assertEquals(350L, PriorityRank.rank(listOf("ichi2")))
        assertEquals(350L, PriorityRank.rank(listOf("spec2")))
        assertEquals(350L, PriorityRank.rank(listOf("gai2")))
    }

    @Test
    fun theStrongestTierWins() {
        assertEquals(150L, PriorityRank.rank(listOf("news2", "ichi1")))
        assertEquals(250L, PriorityRank.rank(listOf("news2", "news1")))
    }

    @Test
    fun theFrequencyBucketOrdersFormsInsideATier() {
        assertEquals(105L, PriorityRank.rank(listOf("ichi1", "nf05")))
        assertEquals(125L, PriorityRank.rank(listOf("ichi1", "nf25")))
        assertEquals(148L, PriorityRank.rank(listOf("ichi1", "nf48")))
        // No band: a neutral middle, so an untagged form neither wins
        // nor loses its tier on frequency alone.
        assertEquals(150L, PriorityRank.rank(listOf("ichi1")))
    }

    @Test
    fun theCompositeOrdersAlexsEatCandidates() {
        // The real JMdict tags of the entries the device search
        // returned for "eat"; the previous min-collapse rank made the
        // first three all 1 and could not tell them apart.
        val taberu = PriorityRank.rank(listOf("ichi1", "news2", "nf25"))
        val kuu = PriorityRank.rank(listOf("ichi1", "news1", "nf33"))
        val meshiagaru = PriorityRank.rank(listOf("ichi1", "news2", "nf45"))
        val kissuru = PriorityRank.rank(listOf("news1", "nf18"))
        val kurau = PriorityRank.rank(listOf("ichi2", "news2", "nf30"))
        val shokugen = PriorityRank.rank(emptyList())

        assertEquals(listOf(125L, 133L, 145L, 218L, 330L, 950L), listOf(taberu, kuu, meshiagaru, kissuru, kurau, shokugen))
        // The everyday-vocabulary tier beats raw newspaper frequency:
        // 喫する is nf18 against 食べる's nf25, yet ranks lower.
        assertTrue(taberu < kissuru)
    }

    @Test
    fun noTagsMeansUnranked() {
        assertEquals(950L, PriorityRank.rank(emptyList()))
        assertEquals(950L, PriorityRank.rank(listOf("unknown-tag")))
    }

    @Test
    fun malformedFrequencyTagsCannotEscapeTheirTier() {
        // nf9999 would otherwise make 10099 and sort behind everything.
        assertEquals(150L, PriorityRank.rank(listOf("ichi1", "nf9999")))
        assertEquals(150L, PriorityRank.rank(listOf("ichi1", "nfxx")))
        assertEquals(150L, PriorityRank.rank(listOf("ichi1", "nf0")))
        // A malformed band must not discard a good one alongside it.
        assertEquals(125L, PriorityRank.rank(listOf("ichi1", "nf9999", "nf25")))
        assertEquals(125L, PriorityRank.rank(listOf("ichi1", "nf25", "nf40")))
    }

    @Test
    fun commonFlagFollowsTheDtdsMarkedSet() {
        // JMdict's DTD: "The entries with news1, ichi1, spec1, spec2
        // and gai1 values are marked with a (P)". This is not the same
        // classification as the rank tiers.
        assertTrue(PriorityRank.isCommon(listOf("ichi1")))
        assertTrue(PriorityRank.isCommon(listOf("news1")))
        assertTrue(PriorityRank.isCommon(listOf("spec1")))
        assertTrue(PriorityRank.isCommon(listOf("spec2")))
        assertTrue(PriorityRank.isCommon(listOf("gai1")))
        assertTrue(PriorityRank.isCommon(listOf("news2", "nf25", "ichi1")))
        assertFalse(PriorityRank.isCommon(listOf("news2", "nf25")))
        assertFalse(PriorityRank.isCommon(listOf("ichi2")))
        assertFalse(PriorityRank.isCommon(listOf("gai2")))
        assertFalse(PriorityRank.isCommon(emptyList()))
    }

    @Test
    fun spec2MarksACommonWordButRanksThirdTier() {
        // お子さま / アル中 / あん摩 carry spec2 and nothing else: the
        // DTD marks them (P), while the rank keeps them behind ichi1
        // and news1 words.
        assertTrue(PriorityRank.isCommon(listOf("spec2")))
        assertEquals(350L, PriorityRank.rank(listOf("spec2")))
    }

    @Test
    fun theEatCandidatesCarryTheExpectedCommonFlags() {
        assertTrue(PriorityRank.isCommon(listOf("ichi1", "news2", "nf25")))
        assertTrue(PriorityRank.isCommon(listOf("news1", "nf18")))
        assertFalse(PriorityRank.isCommon(listOf("ichi2", "news2", "nf30")))
        assertFalse(PriorityRank.isCommon(emptyList()))
    }
}
