package cc.hosaka.okonomi.dictgen

import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.events.EntityDeclaration

class PipelineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Owns both the StAX reader and its underlying stream: XMLStreamReader.close() does not close the stream. */
internal class XmlSource(file: File) : AutoCloseable {
    private val stream = file.inputStream().buffered()
    val reader: XMLStreamReader

    init {
        val factory = XMLInputFactory.newInstance()
        // Entity replacement stays off so DTD entity references (&v1;, &fem;, ...) surface
        // as events whose name IS the short code the schema stores. Predefined XML entities
        // (&amp; etc.) are still replaced as text by the parser.
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, true)
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        reader = try {
            factory.createXMLStreamReader(file.absolutePath, stream)
        } catch (e: Exception) {
            stream.close()
            throw e
        }
    }

    override fun close() {
        reader.close()
        stream.close()
    }
}

internal fun XMLStreamReader.declaredEntityNames(): Set<String> {
    @Suppress("UNCHECKED_CAST")
    val entities = getProperty("javax.xml.stream.entities") as? List<EntityDeclaration>
    return entities.orEmpty().mapNotNull { it.name }.toSet()
}

internal class ElementContent(val text: String, val codes: List<String>)

/** Reads mixed text/entity content of the current element up to its end tag; nested child elements are skipped. */
internal fun XMLStreamReader.readElementContent(declared: Set<String>, sourceName: String): ElementContent {
    val sb = StringBuilder()
    var codes: MutableList<String>? = null
    while (true) {
        when (next()) {
            XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> sb.append(text)
            XMLStreamConstants.START_ELEMENT -> skipElement()
            XMLStreamConstants.ENTITY_REFERENCE -> {
                val name = localName
                if (declared.isNotEmpty() && name !in declared) {
                    throw PipelineException("Unknown entity &$name; in $sourceName")
                }
                (codes ?: mutableListOf<String>().also { codes = it }) += name
            }
            XMLStreamConstants.END_ELEMENT -> return ElementContent(sb.toString().trim(), codes.orEmpty())
        }
    }
}

/** Skips the current element and its whole subtree. Call right after its START_ELEMENT. */
internal fun XMLStreamReader.skipElement() {
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XMLStreamConstants.START_ELEMENT -> depth++
            XMLStreamConstants.END_ELEMENT -> depth--
        }
    }
}

internal fun XMLStreamReader.xmlLang(): String? =
    getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
