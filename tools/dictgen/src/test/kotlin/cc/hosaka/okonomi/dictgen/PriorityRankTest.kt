package cc.hosaka.okonomi.dictgen

import kotlin.test.Test
import kotlin.test.assertEquals

class PriorityRankTest {

    @Test
    fun tierOneTagsRankOne() {
        assertEquals(1L, PriorityRank.rank(listOf("ichi1")))
        assertEquals(1L, PriorityRank.rank(listOf("news1")))
        assertEquals(1L, PriorityRank.rank(listOf("spec1")))
        assertEquals(1L, PriorityRank.rank(listOf("gai1")))
    }

    @Test
    fun tierTwoTagsRankTwo() {
        assertEquals(2L, PriorityRank.rank(listOf("news2")))
        assertEquals(2L, PriorityRank.rank(listOf("ichi2")))
    }

    @Test
    fun nfBucketsScaleWithFrequency() {
        assertEquals(3L, PriorityRank.rank(listOf("nf05")))
        assertEquals(4L, PriorityRank.rank(listOf("nf09")))
        assertEquals(8L, PriorityRank.rank(listOf("nf48")))
    }

    @Test
    fun bestTagWins() {
        assertEquals(1L, PriorityRank.rank(listOf("nf05", "ichi1", "news2")))
    }

    @Test
    fun noTagsMeansUnranked() {
        assertEquals(999L, PriorityRank.rank(emptyList()))
    }
}
