package app.k9mail.html.cleaner

import org.jsoup.nodes.Document

class HtmlProcessor(
    private val customClasses: Set<String>,
    private val htmlHeadProvider: HtmlHeadProvider,
) {
    private val htmlSanitizer = HtmlSanitizer()
    private val outlookMessageCleaner = OutlookMessageCleaner()

    fun processForDisplay(html: String): String {
        return cleanMessageBody(html)
            .addCustomHeadContents()
            .addCustomClasses()
            .withCompactOutput()
            .html()
    }

    /** Cleans the text-view fragment; the message viewer adds its document and styles afterwards. */
    fun processForDisplayFragment(html: String): String {
        return cleanMessageBody(html)
            .withCompactOutput()
            .body().html()
    }

    private fun cleanMessageBody(html: String): Document {
        return htmlSanitizer.sanitize(html)
            .also(outlookMessageCleaner::clean)
    }

    private fun Document.addCustomHeadContents() = apply {
        head().append(htmlHeadProvider.headHtml)
    }

    private fun Document.withCompactOutput() = apply {
        outputSettings()
            .prettyPrint(false)
            .indentAmount(0)
    }

    private fun Document.addCustomClasses() = apply {
        if (customClasses.isNotEmpty()) {
            body().apply {
                customClasses.forEach(::addClass)
            }
        }
    }
}
