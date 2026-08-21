package cc.hosaka.okonomi.dictgen

import java.io.File
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

data class KanjiCharacter(
    val literal: String,
    val grade: Long?,
    val strokeCount: Long,
    val freq: Long?,
    val jlpt: Long?,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val nanori: List<String>,
    val meanings: List<String>,
)

class KanjidicParser(private val file: File) {

    fun parse(onCharacter: (KanjiCharacter) -> Unit) {
        XmlSource(file).use { source ->
            val reader = source.reader
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "character") {
                        onCharacter(readCharacter(reader))
                    }
                }
            } catch (e: XMLStreamException) {
                throw PipelineException("XML parse error in ${file.name}: ${e.message}", e)
            }
        }
    }

    private fun readCharacter(r: XMLStreamReader): KanjiCharacter {
        var literal = ""
        var grade: Long? = null
        var strokeCount = 0L
        var freq: Long? = null
        var jlpt: Long? = null
        val on = mutableListOf<String>()
        val kun = mutableListOf<String>()
        val nanori = mutableListOf<String>()
        val meanings = mutableListOf<String>()
        val none = emptySet<String>()
        while (true) {
            when (r.next()) {
                XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                    "literal" -> literal = r.readElementContent(none, file.name).text
                    "grade" -> grade = r.readElementContent(none, file.name).text.toLong()
                    // First stroke_count is the accepted one; later ones are common miscounts.
                    "stroke_count" -> {
                        val value = r.readElementContent(none, file.name).text.toLong()
                        if (strokeCount == 0L) strokeCount = value
                    }
                    "freq" -> freq = r.readElementContent(none, file.name).text.toLong()
                    "jlpt" -> jlpt = r.readElementContent(none, file.name).text.toLong()
                    "reading" -> {
                        val type = r.getAttributeValue(null, "r_type")
                        val text = r.readElementContent(none, file.name).text
                        when (type) {
                            "ja_on" -> on += text
                            "ja_kun" -> kun += text
                        }
                    }
                    "meaning" -> {
                        val lang = r.getAttributeValue(null, "m_lang")
                        val text = r.readElementContent(none, file.name).text
                        if (lang == null) meanings += text
                    }
                    "nanori" -> nanori += r.readElementContent(none, file.name).text
                    "misc", "reading_meaning", "rmgroup" -> Unit
                    else -> r.skipElement()
                }
                XMLStreamConstants.END_ELEMENT -> if (r.localName == "character") {
                    return KanjiCharacter(literal, grade, strokeCount, freq, jlpt, on, kun, nanori, meanings)
                }
            }
        }
    }
}
