package cc.hosaka.okonomi.dictgen

import java.io.File
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

data class JmdictKanjiForm(val text: String, val commonRank: Long)

data class JmdictReading(
    val text: String,
    val noKanji: Boolean,
    val commonRank: Long,
    val restrictions: String?,
)

data class JmdictSense(
    val pos: String?,
    val misc: String?,
    val field: String?,
    val info: String?,
    val restrictions: String?,
    val glosses: List<String>,
)

data class JmdictEntry(
    val id: Long,
    val kanjiForms: List<JmdictKanjiForm>,
    val readings: List<JmdictReading>,
    val senses: List<JmdictSense>,
)

class JmdictParser(private val file: File) {

    fun parse(onEntry: (JmdictEntry) -> Unit) {
        XmlSource(file).use { source ->
            val reader = source.reader
            var declared = emptySet<String>()
            try {
                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.DTD -> declared = reader.declaredEntityNames()
                        XMLStreamConstants.START_ELEMENT ->
                            if (reader.localName == "entry") onEntry(readEntry(reader, declared))
                    }
                }
            } catch (e: XMLStreamException) {
                throw PipelineException("XML parse error in ${file.name}: ${e.message}", e)
            }
        }
    }

    private fun readEntry(r: XMLStreamReader, declared: Set<String>): JmdictEntry {
        var id = 0L
        val kanjiForms = mutableListOf<JmdictKanjiForm>()
        val readings = mutableListOf<JmdictReading>()
        val senses = mutableListOf<JmdictSense>()
        while (true) {
            when (r.next()) {
                XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                    "ent_seq" -> id = r.readElementContent(declared, file.name).text.toLong()
                    "k_ele" -> kanjiForms += readKEle(r, declared)
                    "r_ele" -> readings += readREle(r, declared)
                    "sense" -> senses += readSense(r, declared)
                    else -> r.skipElement()
                }
                XMLStreamConstants.END_ELEMENT -> if (r.localName == "entry") {
                    if (id == 0L) throw PipelineException("entry without ent_seq in ${file.name}")
                    return JmdictEntry(id, kanjiForms, readings, carryForwardPos(senses))
                }
            }
        }
    }

    // JMdict states pos once and omits it on following senses of the same entry.
    private fun carryForwardPos(senses: List<JmdictSense>): List<JmdictSense> {
        var lastPos: String? = null
        return senses.map { sense ->
            if (sense.pos != null) {
                lastPos = sense.pos
                sense
            } else {
                sense.copy(pos = lastPos)
            }
        }
    }

    private fun readKEle(r: XMLStreamReader, declared: Set<String>): JmdictKanjiForm {
        var text = ""
        val priorities = mutableListOf<String>()
        while (true) {
            when (r.next()) {
                XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                    "keb" -> text = r.readElementContent(declared, file.name).text
                    "ke_pri" -> priorities += r.readElementContent(declared, file.name).text
                    else -> r.skipElement()
                }
                XMLStreamConstants.END_ELEMENT -> if (r.localName == "k_ele") {
                    if (text.isEmpty()) throw PipelineException("k_ele without keb in ${file.name}")
                    return JmdictKanjiForm(text, PriorityRank.rank(priorities))
                }
            }
        }
    }

    private fun readREle(r: XMLStreamReader, declared: Set<String>): JmdictReading {
        var text = ""
        var noKanji = false
        val priorities = mutableListOf<String>()
        val restrictions = mutableListOf<String>()
        while (true) {
            when (r.next()) {
                XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                    "reb" -> text = r.readElementContent(declared, file.name).text
                    "re_nokanji" -> {
                        noKanji = true
                        r.readElementContent(declared, file.name)
                    }
                    "re_restr" -> restrictions += r.readElementContent(declared, file.name).text
                    "re_pri" -> priorities += r.readElementContent(declared, file.name).text
                    else -> r.skipElement()
                }
                XMLStreamConstants.END_ELEMENT -> if (r.localName == "r_ele") {
                    if (text.isEmpty()) throw PipelineException("r_ele without reb in ${file.name}")
                    return JmdictReading(
                        text = text,
                        noKanji = noKanji,
                        commonRank = PriorityRank.rank(priorities),
                        restrictions = restrictions.joinToString(";").ifEmpty { null },
                    )
                }
            }
        }
    }

    private fun readSense(r: XMLStreamReader, declared: Set<String>): JmdictSense {
        val pos = mutableListOf<String>()
        val misc = mutableListOf<String>()
        val field = mutableListOf<String>()
        val infos = mutableListOf<String>()
        val restrictions = mutableListOf<String>()
        val glosses = mutableListOf<String>()
        while (true) {
            when (r.next()) {
                XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                    "pos" -> pos += r.readElementContent(declared, file.name).codes
                    "misc" -> misc += r.readElementContent(declared, file.name).codes
                    "field" -> field += r.readElementContent(declared, file.name).codes
                    "s_inf" -> infos += r.readElementContent(declared, file.name).text
                    "stagk", "stagr" -> restrictions += r.readElementContent(declared, file.name).text
                    "gloss" -> {
                        val lang = r.xmlLang()
                        val content = r.readElementContent(declared, file.name)
                        if (lang == null || lang == "eng") glosses += content.text
                    }
                    "example" -> r.skipElement()
                    else -> r.skipElement()
                }
                XMLStreamConstants.END_ELEMENT -> if (r.localName == "sense") {
                    return JmdictSense(
                        pos = pos.joinToString(",").ifEmpty { null },
                        misc = misc.joinToString(",").ifEmpty { null },
                        field = field.joinToString(",").ifEmpty { null },
                        info = infos.joinToString("; ").ifEmpty { null },
                        restrictions = restrictions.joinToString(";").ifEmpty { null },
                        glosses = glosses,
                    )
                }
            }
        }
    }
}
