package app.k9mail.html.cleaner

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.net.URLEncoder
import org.jsoup.Jsoup
import org.junit.Test

class OutlookSafeLinksRewriterTest {
    private val testSubject = OutlookSafeLinksRewriter()

    @Test
    fun `restores href and visible URL without losing query or fragment`() {
        // Arrange
        val destination = "https://example.org/report?a=1&b=2#totals"
        val link = wrap(destination)
        val document = Jsoup.parse("<a href='$link'>$link</a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(destination)
        assertThat(document.body().text()).isEqualTo(destination)
    }

    @Test
    fun `preserves descriptive label and its formatting`() {
        // Arrange
        val document = Jsoup.parse("<a href='${wrap("https://example.org/")}'><b>Read report</b></a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.html()).isEqualTo("<b>Read report</b>")
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo("https://example.org/")
    }

    @Test
    fun `restores a link that is only text`() {
        // Arrange
        val document = Jsoup.parse("<pre>See ${wrap("https://example.org/")}</pre>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("See https://example.org/")
    }

    @Test
    fun `preserves sentence punctuation around a text link`() {
        // Arrange
        val document = Jsoup.parse("<p>See (${wrap("https://example.org/")}).</p>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("See (https://example.org/).")
    }

    @Test
    fun `does not interpret decoded markup as HTML`() {
        // Arrange
        val destination = "https://example.org/?q=<img/src=x/onerror=alert(1)>"
        val link = wrap(destination)
        val document = Jsoup.parse("<p>$link</p>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.select("img").size).isEqualTo(0)
        assertThat(document.body().text()).isEqualTo(link)
    }

    @Test
    fun `does not double decode percent encoded destination`() {
        // Arrange
        val destination = "https://example.org/a%2Fb?q=hello%20world&nested=%253C"
        val document = Jsoup.parse("<a href='${wrap(destination)}'>Report</a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(destination)
    }

    @Test
    fun `preserves literal and percent encoded plus signs`() {
        // Arrange
        val destination = "https://example.org/?q=a+b&literal=%2B"
        val link = wrap(destination).replace(oldValue = "%2B", newValue = "+")
        val document = Jsoup.parse("<a href='$link'>Report</a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(destination)
    }

    @Test
    fun `accepts UTF8 in destination path`() {
        // Arrange
        val destination = "https://example.org/årsrapport/ø?navn=æ"
        val document = Jsoup.parse("<a href='${wrap(destination)}'>Report</a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(destination)
    }

    @Test
    fun `accepts exact host mixed case and regional subdomains`() {
        listOf(
            "safelinks.protection.outlook.com",
            "NAM12.SafeLinks.Protection.Outlook.COM",
            "eu.safelinks.protection.outlook.com",
            "eu.safelinks.protection.outlook.com.",
        ).forEach { host ->
            // Arrange
            val document = Jsoup.parse("<a href='${wrap("https://example.org/", host)}'>Report</a>")

            // Act
            testSubject.rewrite(document)

            // Assert
            assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo("https://example.org/")
        }
    }

    @Test
    fun `rejects host names that imitate Microsoft`() {
        listOf(
            "notsafelinks.protection.outlook.com",
            "eu.safelinks.protection.outlook.com.evil.example",
            "safelinks-protection.outlook.com",
        ).forEach { host ->
            // Arrange
            val link = wrap("https://example.org/", host)
            val document = Jsoup.parse("<a href='$link'>Report</a>")

            // Act
            testSubject.rewrite(document)

            // Assert
            assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(link)
        }
    }

    @Test
    fun `allows a url parameter that is not the first parameter`() {
        // Arrange
        val link = wrap("https://example.org/").replace(oldValue = "?url=", newValue = "?data=abc&url=")
        val document = Jsoup.parse("<a href='$link'>Report</a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo("https://example.org/")
    }

    @Test
    fun `accepts a url parameter without tracking parameters`() {
        // Arrange
        val link = wrap("https://example.org/").substringBefore("&data=")
        val document = Jsoup.parse("<a href='$link'>Report</a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo("https://example.org/")
    }

    @Test
    fun `restores five levels of wrapping`() {
        // Arrange
        val destination = "https://example.org/"
        var link = destination
        repeat(5) { link = wrap(link) }
        val document = Jsoup.parse("<a href='$link'>Report</a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(destination)
    }

    @Test
    fun `leaves a chain with too many layers unchanged`() {
        // Arrange
        var link = "https://example.org/"
        repeat(6) { link = wrap(link) }
        val document = Jsoup.parse("<a href='$link'>Report</a>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(link)
    }

    @Test
    fun `leaves malformed or ambiguous wrappers unchanged`() {
        val prefix = "https://eu.safelinks.protection.outlook.com/"
        listOf(
            prefix,
            "$prefix?url=",
            "$prefix?url=%ZZ",
            "$prefix?url=%",
            "$prefix?url=https%3A%2F%2Fexample.org%2F%C3%28",
            "$prefix?url=https%3A%2F%2Fexample.org%2F&url=https%3A%2F%2Fother.example%2F",
            wrap("$prefix?data=missing-url"),
        ).forEach { link ->
            // Arrange
            val document = Jsoup.parse("<a href='$link'>Report</a>")

            // Act
            testSubject.rewrite(document)

            // Assert
            assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(link)
        }
    }

    @Test
    fun `does not unwrap non web destinations or misleading authorities`() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,test",
            "intent://example.org/#Intent;end",
            "file:///sdcard/test",
            "content://example.org/test",
            "mailto:somebody@example.org",
            "//example.org/test",
            "https://user:secret@example.org/",
            "https://example.org:99999/",
            "https://example.org\\@other.example/",
            "https://example.org/\nnext",
        ).forEach { destination ->
            // Arrange
            val link = wrap(destination)
            val document = Jsoup.parse("<a href='$link'>Report</a>")

            // Act
            testSubject.rewrite(document)

            // Assert
            assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(link)
        }
    }

    @Test
    fun `preserves normal links including other mail link schemes`() {
        // Arrange
        val html = "<a href='https://example.org/?url=https://other.example/'>Report</a>" +
            "<a href='mailto:somebody@example.org'>Mail</a>"
        val document = Jsoup.parse(html)
        val original = document.outerHtml()

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }

    @Test
    fun `does not rewrite URL looking text inside another URL`() {
        // Arrange
        val text = "https://other.example/?redirect=${wrap("https://example.org/")}"
        val document = Jsoup.parse("<p>$text</p>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.body().text()).isEqualTo(text)
    }

    @Test
    fun `does not change image sources or sender identification notices`() {
        // Arrange
        val html = "<img src='${wrap("https://example.org/image.png")}'>" +
            "<table><tr><td>You don't often get email from this sender. " +
            "<a href='https://aka.ms/LearnAboutSenderIdentification'>Learn more</a></td></tr></table>"
        val document = Jsoup.parse(html)
        val original = document.outerHtml()

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }

    @Test
    fun `handles image map links`() {
        // Arrange
        val document = Jsoup.parse("<map><area href='${wrap("https://example.org/")}'></map>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.selectFirst("area")!!.attr("href")).isEqualTo("https://example.org/")
    }

    @Test
    fun `rewriting twice has the same result as rewriting once`() {
        // Arrange
        val document = Jsoup.parse("<a href='${wrap("https://example.org/")}'>Report</a>")
        testSubject.rewrite(document)
        val original = document.outerHtml()

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }

    @Test
    fun `long ordinary text and excessive URL lengths remain unchanged`() {
        // Arrange
        val text = "x".repeat(100_000) + " " + wrap("https://example.org/" + "a".repeat(70_000))
        val document = Jsoup.parse("<p>$text</p>")

        // Act
        testSubject.rewrite(document)

        // Assert
        assertThat(document.body().text()).isEqualTo(text)
    }

    private fun wrap(destination: String, host: String = "eu.safelinks.protection.outlook.com"): String {
        return "https://$host/?url=${URLEncoder.encode(destination, "UTF-8")}&data=test&sdata=signature&reserved=0"
    }
}
