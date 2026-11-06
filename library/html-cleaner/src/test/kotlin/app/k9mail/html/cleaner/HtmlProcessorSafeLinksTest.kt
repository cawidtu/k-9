package app.k9mail.html.cleaner

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.jsoup.Jsoup
import org.junit.Test

class HtmlProcessorSafeLinksTest {
    private val testSubject = HtmlProcessor(
        customClasses = setOf("message"),
        htmlHeadProvider = object : HtmlHeadProvider {
            override val headHtml = "<style>body { color: black; }</style>"
        },
    )

    @Test
    fun `display pipeline cleans links and preserves sanitization and styling`() {
        // Arrange
        val html = """
            <head><meta http-equiv="refresh" content="0;url=https://other.example/"></head>
            <body><script>alert(1)</script>
            <a href="https://eu.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.org%2F&amp;data=test">Report</a>
            </body>
        """.trimIndent()

        // Act
        val result = Jsoup.parse(testSubject.processForDisplay(html))

        // Assert
        assertThat(result.selectFirst("a")!!.attr("href")).isEqualTo("https://example.org/")
        assertThat(result.select("script, meta[http-equiv=refresh]").size).isEqualTo(0)
        assertThat(result.body().hasClass("message")).isEqualTo(true)
        assertThat(result.head().select("style").size).isEqualTo(1)
    }

    @Test
    fun `display pipeline cannot turn encoded tags into elements`() {
        // Arrange
        val html = "<p>https://eu.safelinks.protection.outlook.com/" +
            "?url=https%3A%2F%2Fexample.org%2F%3Fq%3D%3Cscript%3Ealert(1)%3C%2Fscript%3E&amp;data=test</p>"

        // Act
        val result = Jsoup.parse(testSubject.processForDisplay(html))

        // Assert
        assertThat(result.select("script").size).isEqualTo(0)
        assertThat(result.body().text()).isEqualTo(Jsoup.parse(html).body().text())
    }

    @Test
    fun `display pipeline removes a notice whose help link is wrapped in Safe Links`() {
        // Arrange
        val html = "<table><tr><td>Du får ikke ofte e-mails fra sender@example.org. " +
            "<a href='https://eu.safelinks.protection.outlook.com/" +
            "?url=https%3A%2F%2Faka.ms%2FLearnAboutSenderIdentification&amp;data=test'>Læs mere</a>" +
            "</td></tr></table><p>Her er rapporten.</p>"

        // Act
        val result = Jsoup.parse(testSubject.processForDisplay(html))

        // Assert
        assertThat(result.body().text()).isEqualTo("Her er rapporten.")
        assertThat(result.select("table").size).isEqualTo(0)
        assertThat(result.body().hasClass("message")).isEqualTo(true)
    }

    @Test
    fun `text view fragment removes notices and restores links without document wrappers`() {
        // Arrange
        val html = "<pre><div dir='auto'>[Du får ikke ofte mails fra sender@example.org " +
            "Få mere at vide om, hvorfor dette er vigtigt, på " +
            "<a href='https://aka.ms/LearnAboutSenderIdentification'>Læs mere</a> ]<br><br>" +
            "<a href='https://eu.safelinks.protection.outlook.com/" +
            "?url=https%3A%2F%2Fexample.org%2Freport&amp;data=test'>Rapport</a></div></pre>"

        // Act
        val result = testSubject.processForDisplayFragment(html)
        val document = Jsoup.parse(result)

        // Assert
        assertThat(document.body().text()).isEqualTo("Rapport")
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo("https://example.org/report")
        assertThat(document.select("br").size).isEqualTo(1)
        assertThat(result.startsWith("<pre>")).isEqualTo(true)
        assertThat(result.contains("<html") || result.contains("<head") || result.contains("<body"))
            .isEqualTo(false)
        assertThat(document.select("style").size).isEqualTo(0)
    }

    @Test
    fun `HTML and text fragment routes apply the same message body cleaning`() {
        // Arrange
        val html = "<pre>Du får ikke ofte mails fra sender@example.org. " +
            "<a href='https://aka.ms/LearnAboutSenderIdentification'>Læs mere</a><br><br>" +
            "Message<script>alert(1)</script></pre>"

        // Act
        val fullDocument = Jsoup.parse(testSubject.processForDisplay(html))
        val fragment = testSubject.processForDisplayFragment(html)

        // Assert
        assertThat(fragment).isEqualTo(fullDocument.body().html())
        assertThat(Jsoup.parse(fragment).body().text()).isEqualTo("Message")
    }
}
