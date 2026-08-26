package cc.hosaka.okonomi.dictgen

import java.io.File
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

data class NameRow(
    val id: Long,
    val kanji: String?,
    val reading: String,
    val nameType: String,
    val translation: String,
)

/**
 * The `name_type` codes that make a JMnedict row a person's name, and
 * therefore the only rows the app keeps.
 *
 * JMnedict is two thirds of the shipped database and almost all of it is
 * unreachable material for a reader: places, stations, companies,
 * products, works, and the 53,588 rows tagged only `person`, which are
 * famous individuals rather than names anyone needs read off a form.
 * What is wanted is the ordinary surname and given name — Alex, 2026-08-26:
 * "the most useful thing out of JMnedict are first and last names … I
 * highly doubt I'll look up Tokyo Tower", and "I don't need names of
 * famous people and shit".
 *
 * A row's `name_type` is comma-joined, and one of these codes anywhere in
 * it is enough. That is what keeps the 525 rows typed `fem,person` or
 * `masc,person`: the fem/masc half says the row is an ordinary given
 * name, and the `person` half only adds that somebody notable carries it.
 * Pure `person`, with no given/surname code beside it, is the group that
 * goes.
 *
 * The same rule keeps 16,942 rows that also carry `place` — 東京 is typed
 * `place,surname` — and that is not a leak: JMnedict is saying the string
 * is a surname as well as a place, and it is. Only the person-name codes
 * become chips, so such a row reads as a surname on screen.
 *
 * Measured on the 2026-08-25 sources: 333,481 rows kept of 746,270.
 */
internal val PERSON_NAME_TYPES = setOf("surname", "given", "fem", "masc")

/** Whether a comma-joined [nameType] names a person rather than a thing. */
internal fun isPersonNameType(nameType: String): Boolean =
    nameType.splitToSequence(StoredFormat.CODES).any { it.trim() in PERSON_NAME_TYPES }

class JmnedictParser(private val file: File) {

    /** See [JmdictParser.entityLabels]: JMnedict's name_type codes. */
    var entityLabels: Map<String, String> = emptyMap()
        private set

    fun parse(onRow: (NameRow) -> Unit) {
        XmlSource(file).use { source ->
            val reader = source.reader
            var declared = emptySet<String>()
            try {
                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.DTD -> {
                            val entities = reader.declaredEntities()
                            entityLabels = entities.labels
                            declared = entities.names
                        }
                        XMLStreamConstants.START_ELEMENT ->
                            if (reader.localName == "entry") readEntry(reader, declared, onRow)
                    }
                }
            } catch (e: XMLStreamException) {
                throw PipelineException("XML parse error in ${file.name}: ${e.message}", e)
            }
        }
    }

    private fun readEntry(r: XMLStreamReader, declared: Set<String>, onRow: (NameRow) -> Unit) {
        var id = 0L
        val kanji = mutableListOf<String>()
        val readings = mutableListOf<String>()
        val translations = mutableListOf<Pair<String, String>>()
        while (true) {
            when (r.next()) {
                XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                    "ent_seq" -> id = r.readElementContent(declared, file.name).text.toLong()
                    "keb" -> kanji += r.readElementContent(declared, file.name).text
                    "reb" -> readings += r.readElementContent(declared, file.name).text
                    "trans" -> readTrans(r, declared)?.let { translations += it }
                    "k_ele", "r_ele" -> Unit
                    else -> r.skipElement()
                }
                XMLStreamConstants.END_ELEMENT -> if (r.localName == "entry") {
                    // Deliberately flat: one row per kanji x reading x translation combination.
                    for ((types, translation) in translations) {
                        for (reading in readings) {
                            if (kanji.isEmpty()) {
                                onRow(NameRow(id, null, reading, types, translation))
                            } else {
                                for (k in kanji) onRow(NameRow(id, k, reading, types, translation))
                            }
                        }
                    }
                    return
                }
            }
        }
    }

    private fun readTrans(r: XMLStreamReader, declared: Set<String>): Pair<String, String>? {
        val types = mutableListOf<String>()
        val details = mutableListOf<String>()
        while (true) {
            when (r.next()) {
                XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                    "name_type" -> types += r.readElementContent(declared, file.name).codes
                    "trans_det" -> {
                        val lang = r.xmlLang()
                        val content = r.readElementContent(declared, file.name)
                        if (lang == null || lang == "eng") details += content.text
                    }
                    else -> r.skipElement()
                }
                XMLStreamConstants.END_ELEMENT -> if (r.localName == "trans") {
                    if (details.isEmpty()) return null
                    return types.joinToString(StoredFormat.CODES) to details.joinToString(StoredFormat.TEXT)
                }
            }
        }
    }
}
