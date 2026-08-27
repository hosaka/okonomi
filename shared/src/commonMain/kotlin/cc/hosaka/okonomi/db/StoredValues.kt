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

    /**
     * Stroke paths: `kanji_stroke_order.paths`. SVG path data uses
     * commas and semicolons freely, so neither of the separators above
     * could carry it; no KanjiVG `d` value contains a newline.
     */
    const val STROKE_PATH_SEPARATOR = '\n'

    /** The stored codes of one column, blanks dropped. */
    fun codes(value: String?): List<String> = split(value, CODE_SEPARATOR)

    /** The stored restrictions of one column, blanks dropped. */
    fun restrictions(value: String?): List<String> = split(value, RESTRICTION_SEPARATOR)

    /** The stroke paths of one character, in order, blanks dropped. */
    fun strokePaths(value: String?): List<String> = split(value, STROKE_PATH_SEPARATOR)

    private fun split(value: String?, separator: Char): List<String> =
        value?.split(separator)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
