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

class JmnedictParser(private val file: File) {

    fun parse(onRow: (NameRow) -> Unit) {
        XmlSource(file).use { source ->
            val reader = source.reader
            var declared = emptySet<String>()
            try {
                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.DTD -> declared = reader.declaredEntityNames()
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
                    return types.joinToString(",") to details.joinToString("; ")
                }
            }
        }
    }
}
