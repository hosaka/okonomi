package cc.hosaka.okonomi.dictgen

/**
 * Turns JMdict ke_pri/re_pri tags into one sortable rank, lower being
 * more common.
 *
 * The rank is a composite `tier * 100 + bucket`. The tier is the
 * strongest priority class the form carries; the bucket is its `nfXX`
 * newspaper-frequency band. Taking the best tag alone (the previous
 * behaviour) collapsed the corpus into three values and threw away the
 * frequency band that actually separates common words: 食べる's
 * `ichi1 news2 nf25` and 召し上がる's `ichi1 news2 nf45` both became 1.
 * The composite keeps the tier dominant while the band orders words
 * inside it — 食べる 125 < 食う 133 < 召し上がる 145 < 喫する 218 <
 * 食らう 330 < 食言 950.
 *
 * The tier deliberately outranks raw newspaper frequency: 喫する is
 * more frequent in newspapers (nf18) than 食べる (nf25), but only
 * 食べる carries `ichi1`, the everyday-vocabulary marker.
 */
object PriorityRank {
    private const val TIER_FACTOR = 100L
    private const val NO_TIER = 9L

    /**
     * Band given to a form that carries no `nfXX` tag. It sorts after
     * every real band (nf01..nf48) on purpose: such a form is absent
     * from the frequency list its tier-mates were measured on, so
     * within a tier it ranks behind everything that was measured.
     */
    private const val UNBANDED = 50L

    private const val MIN_BAND = 1L
    private const val MAX_BAND = 99L

    /**
     * The tags JMdict's own DTD calls "(P)": «The entries with news1,
     * ichi1, spec1, spec2 and gai1 values are marked with a (P)». This
     * is jisho's "common word" set and is deliberately NOT the same
     * classification as [tier] — `news1` is a second tier there, and
     * `spec2` a third, yet both mark a common word.
     */
    private val COMMON_MARKER_TAGS = setOf("ichi1", "news1", "spec1", "spec2", "gai1")

    fun rank(tags: List<String>): Long = tier(tags) * TIER_FACTOR + band(tags)

    /** True for the tags JMdict marks "(P)" and jisho shows as common. */
    fun isCommon(tags: List<String>): Boolean = tags.any { it in COMMON_MARKER_TAGS }

    private fun tier(tags: List<String>): Long = when {
        tags.any { it == "ichi1" || it == "spec1" || it == "gai1" } -> 1L
        tags.any { it == "news1" } -> 2L
        tags.any { it == "ichi2" || it == "spec2" || it == "gai2" || it == "news2" } -> 3L
        else -> NO_TIER
    }

    /**
     * The best `nfXX` band the form carries, clamped to a two-digit
     * value so a malformed tag can never grow past its tier and invert
     * the ordering. Unparseable `nf` tags are skipped rather than
     * taken as an answer, so one bad tag does not discard a good one.
     */
    private fun band(tags: List<String>): Long = tags
        .filter { it.startsWith("nf") }
        .mapNotNull { it.removePrefix("nf").toLongOrNull() }
        .filter { it in MIN_BAND..MAX_BAND }
        .minOrNull()
        ?: UNBANDED
}
