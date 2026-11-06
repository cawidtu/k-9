package app.k9mail.html.cleaner

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Shared Outlook cleanup for message rendering and list previews. */
class OutlookMessageCleaner {
    private val safeLinksRewriter = OutlookSafeLinksRewriter()
    private val senderBannerRemover = OutlookSenderBannerRemover()

    internal fun clean(document: Document) {
        safeLinksRewriter.rewrite(document)
        senderBannerRemover.remove(document)
    }

    fun cleanHtml(html: String): String {
        val document = Jsoup.parse(html)
        clean(document)
        document.outputSettings().prettyPrint(false)
        return document.html()
    }

    fun cleanPlainText(text: String): String {
        val document = Document.createShell("")
        val content = document.body().appendElement("pre").text(text)
        clean(document)
        return content.wholeText()
    }

    /** Old cached previews have collapsed line breaks and URL placeholders instead of links. */
    fun cleanCachedPreview(preview: String): String {
        val singleLine = preview.replace(oldChar = '\r', newChar = ' ').replace(oldChar = '\n', newChar = ' ')
        if (!NOTICE_START.containsMatchIn(singleLine)) return preview
        val normalized = CACHED_HELP_URL.replaceFirst(input = singleLine, replacement = "$1$IDENTIFICATION_URL$2")
        val document = Document.createShell("")
        val content = document.body().appendElement("pre").text(normalized)
        senderBannerRemover.remove(document)
        val cleaned = content.wholeText()
        return when {
            cleaned != normalized -> cleaned.trim()
            TRUNCATED_NOTICE.matches(singleLine) -> ""
            else -> preview
        }
    }

    private companion object {
        const val IDENTIFICATION_URL = "https://aka.ms/LearnAboutSenderIdentification"
        val NOTICE_START = Regex("^\\s*\\[?(?:du|you)\\b", RegexOption.IGNORE_CASE)
        val CACHED_HELP_URL = Regex(
            "^(\\s*\\[[^\\]\\r\\n]{0,512}?\\b(?:på|at)\\s+)(?:\\.{3}|…)(\\s*\\])",
            RegexOption.IGNORE_CASE,
        )
        val TRUNCATED_NOTICE = Regex(
            "^\\s*\\[?(?:du (?:får|modtager) (?:ikke ofte|sjældent) (?:e[-‐‑]?mails?|mails?) fra|" +
                "you (?:don't|don’t|do not) often (?:get|receive) e-?mails? from)" +
                "\\s+[^\\s@]+@[^\\s@]+\\s+(?:få (?:mere at vide|at vide)|learn why).*…$",
            RegexOption.IGNORE_CASE,
        )
    }
}
