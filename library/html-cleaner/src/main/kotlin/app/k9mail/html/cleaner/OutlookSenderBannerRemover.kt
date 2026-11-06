package app.k9mail.html.cleaner

import java.net.URI
import java.net.URISyntaxException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** Removes the first-contact notice without deleting the surrounding message container. */
internal class OutlookSenderBannerRemover {
    fun remove(document: Document) {
        val contents = MessageText(document.body())
        val ranges = BANNER_HEADER.findAll(contents.text).mapNotNull { header ->
            var end = header.range.last + 1
            val helpEnd = contents.findHelpEnd(end)
            val plainHelp = BANNER_HELP.matchAt(contents.text, end)
            end = helpEnd ?: plainHelp?.range?.last?.plus(1) ?: end
            end = skipBannerSpacing(contents.text, end)
            if (header.groups[1] != null) {
                if (contents.text.getOrNull(end) != ']') return@mapNotNull null
                end++
                end = skipBannerSpacing(contents.text, end)
            }
            val hasHelp = helpEnd != null || plainHelp != null
            val atLineEnd = end == contents.text.length || contents.text[end] in "\r\n"
            if (!hasHelp && !atLineEnd) return@mapNotNull null
            if (end < contents.text.length && contents.text[end] == '\r') end++
            if (end < contents.text.length && contents.text[end] == '\n') end++
            header.range.first until end
        }.toList()

        if (ranges.isNotEmpty()) contents.remove(ranges)
    }

    private class MessageText(root: Element) : NodeVisitor {
        private val buffer = StringBuilder()
        private val textNodes = mutableListOf<TextSpan>()
        private val lineBreaks = mutableListOf<BreakSpan>()
        private val linkStarts = mutableMapOf<Element, Int>()
        val helpLinks = mutableListOf<HelpLink>()
        val text: String

        init {
            NodeTraversor.traverse(this, root)
            text = buffer.toString()
        }

        override fun head(node: Node, depth: Int) {
            when (node) {
                is TextNode -> {
                    val start = buffer.length
                    buffer.append(node.wholeText)
                    textNodes += TextSpan(node, start, buffer.length)
                }

                is Element -> {
                    if (node.normalName() == "br") lineBreaks += BreakSpan(node, buffer.length)
                    if (node.isBlock || node.normalName() == "br") buffer.append('\n')
                    if (node.normalName() == "a" && isIdentificationLink(node.attr("href"))) {
                        linkStarts[node] = buffer.length
                    }
                }
            }
        }

        override fun tail(node: Node, depth: Int) {
            if (node is Element) {
                linkStarts.remove(node)?.let { start -> helpLinks += HelpLink(node, start, buffer.length) }
                if (node.isBlock) buffer.append('\n')
            }
        }

        fun findHelpEnd(start: Int): Int? {
            val linkEnd = helpLinks.firstNotNullOfOrNull { candidate ->
                if (candidate.start < start) {
                    null
                } else {
                    matchingHelpEnd(
                        value = text,
                        afterHeader = start,
                        linkStart = candidate.start,
                        linkEnd = candidate.end,
                    )
                }
            }
            if (linkEnd != null) return linkEnd
            return WEB_URL.findAll(text, start).firstNotNullOfOrNull { candidate ->
                val url = candidate.value.trimEnd { it in URL_PUNCTUATION }
                if (!isIdentificationLink(url)) {
                    null
                } else {
                    matchingHelpEnd(
                        value = text,
                        afterHeader = start,
                        linkStart = candidate.range.first,
                        linkEnd = candidate.range.first + url.length,
                    )
                }
            }
        }

        fun remove(ranges: List<IntRange>) {
            val parents = mutableSetOf<Element>()
            textNodes.forEach { span ->
                val overlaps = ranges.filter { it.first < span.end && it.last >= span.start }
                if (overlaps.isNotEmpty()) {
                    val updated = StringBuilder(span.node.wholeText)
                    overlaps.asReversed().forEach { range ->
                        updated.delete(
                            (range.first - span.start).coerceAtLeast(0),
                            (range.last + 1 - span.start).coerceAtMost(updated.length),
                        )
                    }
                    span.node.text(updated.toString())
                    (span.node.parent() as? Element)?.let(parents::add)
                }
            }
            helpLinks.filter { link -> ranges.any { it.first <= link.start && it.last + 1 >= link.end } }
                .forEach { link ->
                    link.element.parent()?.let(parents::add)
                    link.element.remove()
                }
            lineBreaks.filter { lineBreak -> ranges.any { lineBreak.offset in it } }
                .forEach { lineBreak ->
                    lineBreak.element.parent()?.let(parents::add)
                    lineBreak.element.remove()
                }
            parents.forEach(::removeEmptyAncestors)
        }

        private fun removeEmptyAncestors(start: Element) {
            var element: Element? = start
            while (element != null && element.normalName() !in ROOT_TAGS && element.text().isBlank()) {
                val parent = element.parent()
                if (parent == null || element.select(MESSAGE_CONTENT).isNotEmpty()) break
                element.remove()
                element = parent
            }
        }
    }

    private data class TextSpan(val node: TextNode, val start: Int, val end: Int)
    private data class HelpLink(val element: Element, val start: Int, val end: Int)
    private data class BreakSpan(val element: Element, val offset: Int)

    private companion object {
        val ROOT_TAGS = setOf("html", "head", "body")
        const val MESSAGE_CONTENT = "img, picture, audio, video, iframe, object, embed, hr, input"
        const val URL_PUNCTUATION = ".,;!)"
        val WEB_URL = Regex("https?://[^\\s<>\\[\\]]+", RegexOption.IGNORE_CASE)
        val PREFIX = (
            "(?:du (?:får|modtager) (?:ikke ofte|sjældent) (?:e[-‐‑]?mails?|mails?) fra|" +
                "you (?:don't|don’t|do not) often (?:get|receive) (?:e-?mails?) from)"
            )
            .replace(oldValue = " ", newValue = "[\\s\\u00a0]+")
        val BANNER_HEADER = Regex(
            "^[\\t \\u00a0]*(\\[)?$PREFIX[\\s\\u00a0]+<?[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}.\\-]+>?[.]?",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        val HELP_TEXT = (
            "(?:learn why this is important|" +
                "(?:få (?:mere at vide om|at vide)|læs|se),? hvorfor (?:dette|det) er vigtigt)"
            ).replace(oldValue = " ", newValue = "[\\s\\u00a0]+")
        val BANNER_HELP = Regex(
            "[\\s\\u00a0.]*$HELP_TEXT[.!]?",
            RegexOption.IGNORE_CASE,
        )
        val HELP_GAP = Regex(
            "[\\s\\u00a0.]*$HELP_TEXT[\\s\\u00a0.!]*(?:,?[\\s\\u00a0]*(?:på|at)[\\s\\u00a0]+)?",
            RegexOption.IGNORE_CASE,
        )

        fun isHelpGap(value: String): Boolean =
            value.all { isBannerSpacing(it) || it == '\r' || it == '\n' } || HELP_GAP.matches(value)

        fun matchingHelpEnd(value: String, afterHeader: Int, linkStart: Int, linkEnd: Int): Int? {
            val gap = value.substring(startIndex = afterHeader, endIndex = linkStart)
            val angled = gap.endsWith('<')
            val prefix = if (angled) gap.dropLast(1) else gap
            return when {
                !isHelpGap(prefix) -> null
                angled && value.getOrNull(linkEnd) != '>' -> null
                angled -> linkEnd + 1
                else -> linkEnd
            }
        }

        fun isBannerSpacing(character: Char): Boolean = character == ' ' || character == '\t' ||
            character == '\u00a0' || character == '.'

        fun skipBannerSpacing(value: String, start: Int): Int {
            var end = start
            while (end < value.length && isBannerSpacing(value[end])) end++
            return end
        }

        fun isIdentificationLink(value: String): Boolean {
            val uri = try {
                URI(value)
            } catch (_: URISyntaxException) {
                return false
            }
            return (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
                uri.host.equals("aka.ms", ignoreCase = true) && uri.rawUserInfo == null &&
                uri.path.removeSuffix("/").equals("/LearnAboutSenderIdentification", ignoreCase = true)
        }
    }
}
