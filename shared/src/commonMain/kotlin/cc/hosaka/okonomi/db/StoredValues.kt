package cc.hosaka.okonomi.db

/**
 * How the generator folds repeated values into the single columns the
 * schema stores, and how everything that reads them splits them again.
 * dictgen names the same two separators on its side (StoredFormat); this
 * is the reading end of that contract.
 */
object StoredValues {
    /** Short codes: `sense.pos`, `misc`, `field`, `dial`, `name_type`. */
    const val CODE_SEPARATOR = ','

    /** Restrictions: `reading.restrictions`, `sense.restrictions`. */
    const val RESTRICTION_SEPARATOR = ';'

    /** The stored codes of one column, blanks dropped. */
    fun codes(value: String?): List<String> = split(value, CODE_SEPARATOR)

    /** The stored restrictions of one column, blanks dropped. */
    fun restrictions(value: String?): List<String> = split(value, RESTRICTION_SEPARATOR)

    private fun split(value: String?, separator: Char): List<String> =
        value?.split(separator)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
