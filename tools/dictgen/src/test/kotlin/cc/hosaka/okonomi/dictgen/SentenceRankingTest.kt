package cc.hosaka.okonomi.dictgen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which ten sentences an entry keeps. The rule replaced pure ascending
 * length, which filled 食べる's ten with 7-to-9 character fragments and
 * led 死ぬ with 死ね！, so each clause here is one thing that ordering
 * got wrong.
 */
class SentenceRankingTest {

    private fun link(
        id: Long,
        length: Int,
        checked: Boolean = false,
        key: String = "s$id",
    ) = SentenceLink(id, length, checked, key)

    private fun ordered(vararg links: SentenceLink): List<Long> =
        links.sortedWith(SENTENCE_ORDER).map { it.sentenceId }

    @Test
    fun `a readable sentence beats a shorter one outside the band`() {
        // 死ね！ is four characters and would win on length alone.
        assertEquals(listOf(2L, 1L), ordered(link(1L, 4), link(2L, 12)))
        assertEquals(listOf(2L, 1L), ordered(link(1L, 40), link(2L, 12)))
    }

    @Test
    fun `the band's edges are inclusive`() {
        assertTrue(link(1L, READABLE_LENGTH_MIN).inReadableBand)
        assertTrue(link(1L, READABLE_LENGTH_MAX).inReadableBand)
        assertTrue(!link(1L, READABLE_LENGTH_MIN - 1).inReadableBand)
        assertTrue(!link(1L, READABLE_LENGTH_MAX + 1).inReadableBand)
    }

    @Test
    fun `a verified sentence leads inside the band`() {
        // Shorter, but nobody checked it against this entry.
        assertEquals(listOf(2L, 1L), ordered(link(1L, 9), link(2L, 18, checked = true)))
    }

    @Test
    fun `length breaks the tie once both are verified`() {
        assertEquals(
            listOf(2L, 1L),
            ordered(link(1L, 18, checked = true), link(2L, 9, checked = true)),
        )
    }

    @Test
    fun `identical candidates fall back on the sentence id`() {
        assertEquals(listOf(7L, 9L), ordered(link(9L, 12), link(7L, 12)))
    }

    @Test
    fun `out of band sentences are ordered among themselves too`() {
        assertEquals(listOf(2L, 1L), ordered(link(1L, 40), link(2L, 4)))
    }
}

class SentenceKeyTest {

    @Test
    fun `sentences differing only in how they end share a key`() {
        assertEquals(SentenceKey.of("教室で食べるの。"), SentenceKey.of("教室で食べるの？"))
        assertEquals(SentenceKey.of("行くよ"), SentenceKey.of("行くよ！"))
    }

    @Test
    fun `sentences differing anywhere else do not`() {
        // Deliberately not fuzzy: one particle apart is two sentences.
        assertTrue(SentenceKey.of("私は行く。") != SentenceKey.of("私が行く。"))
        assertTrue(SentenceKey.of("犬だ。") != SentenceKey.of("猫だ。"))
    }
}

class TopSentencesTest {

    private fun link(
        id: Long,
        length: Int,
        checked: Boolean = false,
        key: String = "s$id",
    ) = SentenceLink(id, length, checked, key)

    private fun TopSentences.offerAll(vararg links: SentenceLink) = links.forEach { offer(it) }

    @Test
    fun `keeps only the best candidates, however many are offered`() {
        val top = TopSentences(3)
        // Offered worst first, so nothing can pass by arriving early.
        top.offerAll(link(1L, 40), link(2L, 30), link(3L, 20), link(4L, 12), link(5L, 9))

        assertEquals(listOf(5L, 4L, 3L), top.ordered().map { it.sentenceId })
    }

    @Test
    fun `a candidate no better than the ten already kept changes nothing`() {
        val top = TopSentences(2)
        top.offerAll(link(1L, 12), link(2L, 14))
        top.offer(link(3L, 40))

        assertEquals(listOf(1L, 2L), top.ordered().map { it.sentenceId })
    }

    @Test
    fun `near duplicates collapse to the better of the two`() {
        val top = TopSentences(10)
        top.offerAll(
            link(1L, 12, key = "教室で食べるの"),
            link(2L, 12, checked = true, key = "教室で食べるの"),
            link(3L, 12, key = "別の文"),
        )

        assertEquals(
            listOf(2L, 3L),
            top.ordered().map { it.sentenceId },
            "one sentence must not take two of an entry's ten slots",
        )
    }

    @Test
    fun `a worse near duplicate arriving later is dropped`() {
        val top = TopSentences(10)
        top.offerAll(
            link(1L, 12, checked = true, key = "教室で食べるの"),
            link(2L, 12, key = "教室で食べるの"),
        )

        assertEquals(listOf(1L), top.ordered().map { it.sentenceId })
    }

    @Test
    fun `a better near duplicate replaces its twin instead of joining it`() {
        val top = TopSentences(2)
        top.offerAll(
            link(1L, 14, key = "同じ"),
            link(2L, 10, key = "同じ"),
            link(3L, 12, key = "別"),
        )

        assertEquals(
            listOf(2L, 3L),
            top.ordered().map { it.sentenceId },
            "one sentence must not hold two of the slots a distinct one could use",
        )
    }

    @Test
    fun `an entry with nothing offered keeps nothing`() {
        assertEquals(emptyList(), TopSentences(10).ordered())
    }
}
