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

/**
 * What a source's DTD declared. [names] is every entity name, which is
 * what entity references are validated against; [labels] maps the
 * subset that carries usable replacement text to it. The two are
 * deliberately separate: an entity without replacement text is still a
 * declared entity, and treating it as undeclared would fail generation
 * over a missing label.
 *
 * The names are the short codes the schema stores (`v1`, `fem`, ...)
 * and the expansions their human-readable meanings, which the app shows
 * as chip labels. A name declared twice keeps its first declaration,
 * which is the one XML considers effective.
 */
internal class DeclaredEntities(
    val names: Set<String>,
    val labels: Map<String, String>,
)

internal fun XMLStreamReader.declaredEntities(): DeclaredEntities {
    @Suppress("UNCHECKED_CAST")
    val entities = getProperty("javax.xml.stream.entities") as? List<EntityDeclaration>
    val names = LinkedHashSet<String>()
    val labels = LinkedHashMap<String, String>()
    entities.orEmpty().forEach { entity ->
        val name = entity.name ?: return@forEach
        names += name
        val text = entity.replacementText?.trim().orEmpty()
        if (text.isNotEmpty() && name !in labels) {
            labels[name] = text
        }
    }
    return DeclaredEntities(names, labels)
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
