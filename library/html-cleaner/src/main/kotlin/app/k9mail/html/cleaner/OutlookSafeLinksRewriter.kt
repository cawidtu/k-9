package app.k9mail.html.cleaner

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.util.Locale
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** Restores web destinations locally, without following or fetching the Safe Link. */
internal class OutlookSafeLinksRewriter {
    fun rewrite(document: Document) {
        document.select("a[href], area[href]").forEach { element ->
            val destination = unwrap(element.attr("href")) ?: return@forEach
            element.attr("href", destination)
        }

        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    if (node is TextNode) {
                        node.text(rewriteText(node.wholeText))
                    }
                }

                override fun tail(node: Node, depth: Int) = Unit
            },
            document.body(),
        )
    }

    private fun rewriteText(text: String): String = WEB_URL.replace(text) { match ->
        val candidate = match.value.trimEnd { it in TRAILING_PUNCTUATION }
        val destination = unwrap(candidate)
        if (destination == null) match.value else destination + match.value.substring(candidate.length)
    }

    @Suppress("ReturnCount") // Reject an entire chain as soon as one layer is invalid.
    private fun unwrap(value: String): String? {
        var current = value
        repeat(MAX_DEPTH) {
            val wrapper = parseWebUri(current) ?: return null
            if (!isSafeLink(wrapper)) return if (current == value) null else current

            val query = wrapper.rawQuery ?: return null
            val parameters = query.split('&').filter { parameter -> parameter.substringBefore('=') == "url" }
            if (parameters.size != 1) return null

            val encoded = parameters.single().substringAfter('=', "")
            current = decodePercentEncoding(encoded) ?: return null
        }

        // Do not partially unwrap a malformed or excessively nested chain.
        val destination = parseWebUri(current) ?: return null
        return current.takeUnless { isSafeLink(destination) }
    }

    @Suppress("ReturnCount") // Reject invalid input before inspecting the next URI component.
    private fun parseWebUri(value: String): URI? {
        if (value.length > MAX_URL_LENGTH || value.any { it.isISOControl() || it == '\\' }) return null

        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return null
        }

        if (!uri.scheme.equals("https", ignoreCase = true) && !uri.scheme.equals("http", ignoreCase = true)) {
            return null
        }
        if (uri.host.isNullOrEmpty() || uri.rawUserInfo != null || uri.port !in -1..MAX_PORT) return null

        return uri
    }

    private fun isSafeLink(uri: URI): Boolean {
        val host = uri.host.lowercase(Locale.ROOT).removeSuffix(".")
        return host == SAFE_LINKS_HOST || host.endsWith(".$SAFE_LINKS_HOST")
    }

    /** Percent-decodes once; a literal '+' is not form-encoded whitespace. */
    @Suppress("ReturnCount") // Fail closed at the first malformed escape or invalid UTF-8 sequence.
    private fun decodePercentEncoding(value: String): String? {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '%') {
                result.append(value[index++])
                continue
            }

            val bytes = ByteArrayOutputStream()
            while (index < value.length && value[index] == '%') {
                if (index + 2 >= value.length) return null
                val high = value[index + 1].digitToIntOrNull(HEX_RADIX) ?: return null
                val low = value[index + 2].digitToIntOrNull(HEX_RADIX) ?: return null
                bytes.write(high * HEX_RADIX + low)
                index += ESCAPE_LENGTH
            }

            val decoded = try {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray()))
            } catch (_: CharacterCodingException) {
                return null
            }
            result.append(decoded)
        }
        return result.toString()
    }

    private companion object {
        const val SAFE_LINKS_HOST = "safelinks.protection.outlook.com"
        const val MAX_DEPTH = 5
        const val MAX_URL_LENGTH = 65_536
        const val MAX_PORT = 65_535
        const val HEX_RADIX = 16
        const val ESCAPE_LENGTH = 3
        const val TRAILING_PUNCTUATION = ".,;:!?)]}"
        val WEB_URL = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
    }
}
