package cc.hosaka.okonomi.dictgen

import kotlin.math.ceil

/**
 * Collapses JMdict ke_pri/re_pri tags into one sortable rank.
 * Lower is more common; forms with no priority tags rank 999.
 */
object PriorityRank {
    const val UNRANKED = 999L

    fun rank(tags: List<String>): Long = tags.minOfOrNull(::rankOf) ?: UNRANKED

    private fun rankOf(tag: String): Long = when {
        tag == "ichi1" || tag == "news1" || tag == "spec1" || tag == "gai1" -> 1L
        tag == "ichi2" || tag == "news2" || tag == "spec2" || tag == "gai2" -> 2L
        tag.startsWith("nf") -> {
            val bucket = tag.removePrefix("nf").toLongOrNull() ?: return UNRANKED
            2L + ceil(bucket / 8.0).toLong()
        }
        else -> UNRANKED
    }
}
