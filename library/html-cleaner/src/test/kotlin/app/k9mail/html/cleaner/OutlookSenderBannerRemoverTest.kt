package app.k9mail.html.cleaner

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.jsoup.Jsoup
import org.junit.Test

class OutlookSenderBannerRemoverTest {
    private val testSubject = OutlookSenderBannerRemover()
    private val help = "<a href='https://aka.ms/LearnAboutSenderIdentification'>Læs mere</a>"

    @Test
    fun `removes angle wrapped help URL in the first MIME text part after K9 conversion`() {
        // Arrange: K-9 represents original angle brackets as text outside the generated anchor.
        val document = Jsoup.parse(
            "<pre><div dir='auto'>Du får ikke ofte mails fra sender@example.org. " +
                "Få mere at vide om, hvorfor dette er vigtigt&lt;" +
                "<a href='https://aka.ms/LearnAboutSenderIdentification'>" +
                "https://aka.ms/LearnAboutSenderIdentification</a>&gt;<br><br>Message</div></pre>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
        assertThat(document.select("a").size).isEqualTo(0)
        assertThat(document.select("br").size).isEqualTo(1)
    }

    @Test
    fun `removes angle wrapped help URL without an anchor`() {
        // Arrange
        val document = Jsoup.parse(
            "<pre>Du får ikke ofte mails fra sender@example.org. " +
                "Få mere at vide om, hvorfor dette er vigtigt&lt;" +
                "https://aka.ms/LearnAboutSenderIdentification&gt;\n\nMessage</pre>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.selectFirst("pre")!!.wholeText()).isEqualTo("\nMessage")
    }

    @Test
    fun `removes only the banner line break in the flowed MIME part after K9 conversion`() {
        // Arrange
        val document = Jsoup.parse(
            "<pre><div dir='auto'>[Du får ikke ofte mails fra sender@example.org " +
                "Få mere at vide om, hvorfor dette er vigtigt, på " +
                "<a href='https://aka.ms/LearnAboutSenderIdentification'>" +
                "https://aka.ms/LearnAboutSenderIdentification</a> ]<br><br>Message<br>Next line</div></pre>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message Next line")
        assertThat(document.select("br").size).isEqualTo(2)
    }

    @Test
    fun `removes the first MIME HTML banner with revert styling and formatted newlines`() {
        // Arrange
        val document = Jsoup.parse(
            "<table style='display:revert!important'><tr><td><div style='white-space:revert!important'>\n" +
                "Du får ikke ofte mails fra sender@example.org. " +
                "<a href='https://aka.ms/LearnAboutSenderIdentification' style='display:revert!important'>\n" +
                "Få mere at vide om, hvorfor dette er vigtigt</a> </div>\n</td></tr></table>" +
                "<p>Message</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
        assertThat(document.select("table").size).isEqualTo(0)
    }

    @Test
    fun `removes bracketed Danish notice from the attached message with a linkified URL`() {
        // Arrange: same wording, brackets and CRLF as the MSG body, with an anonymized sender.
        val document = Jsoup.parse(
            "<pre>[Du får ikke ofte mails fra sender@example.org " +
                "Få mere at vide om, hvorfor dette er vigtigt, på " +
                "<a href='https://aka.ms/LearnAboutSenderIdentification'>" +
                "https://aka.ms/LearnAboutSenderIdentification</a> ]\r\n\r\n" +
                "Dear all,\r\n\r\nThe message body must remain unchanged.</pre>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.selectFirst("pre")!!.wholeText())
            .isEqualTo("\r\nDear all,\r\n\r\nThe message body must remain unchanged.")
        assertThat(document.select("a").size).isEqualTo(0)
    }

    @Test
    fun `removes the attached message notice when the URL is plain text`() {
        // Arrange
        val document = Jsoup.parse(
            "<pre>[Du får ikke ofte mails fra sender@example.org " +
                "Få mere at vide om, hvorfor dette er vigtigt, på " +
                "https://aka.ms/LearnAboutSenderIdentification ]\r\n\r\nMessage</pre>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.selectFirst("pre")!!.wholeText()).isEqualTo("\r\nMessage")
    }

    @Test
    fun `removes an English bracketed notice containing a plain help URL`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>[You don't often get email from sender@example.org. " +
                "Learn why this is important at https://aka.ms/LearnAboutSenderIdentification ]</p>" +
                "<p>Message</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
    }

    @Test
    fun `preserves a bracketed explanation containing extra prose after the help link`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>[Du får ikke ofte mails fra sender@example.org. $help Dette er et eksempel i vejledningen.]</p>",
        )
        val original = document.outerHtml()

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }

    @Test
    fun `removes Danish notice and its empty table`() {
        // Arrange
        val document = Jsoup.parse(
            "<table><tr><td>Du får ikke ofte e-mails fra afsender@example.org. $help</td></tr></table>" +
                "<p>Her er rapporten.</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().html()).isEqualTo("<p>Her er rapporten.</p>")
    }

    @Test
    fun `recognizes common Danish and English variants`() {
        listOf(
            "Du får ikke ofte e-mails fra",
            "Du modtager ikke ofte mails fra",
            "Du får sjældent mail fra",
            "Du får ikke ofte e‐mails fra",
            "Du får ikke ofte e‑mails fra",
            "You don't often get email from",
            "You don’t often get emails from",
            "You do not often receive email from",
        ).forEach { prefix ->
            // Arrange
            val document = Jsoup.parse("<div>$prefix sender@example.org. $help</div><p>Message</p>")

            // Act
            testSubject.remove(document)

            // Assert
            assertThat(document.body().text()).isEqualTo("Message")
        }
    }

    @Test
    fun `recognizes formatting and mailto links inside the notice`() {
        // Arrange
        val document = Jsoup.parse(
            "<div><b>Du får ikke</b> <span>ofte e-mails fra</span> " +
                "<a href='mailto:afsender@example.org'>afsender@example.org</a>. $help</div><p>Message</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
        assertThat(document.select("a").size).isEqualTo(0)
    }

    @Test
    fun `recognizes HTML whitespace and case differences`() {
        // Arrange
        val document = Jsoup.parse(
            "<div>DU&nbsp;FÅR\nIKKE OFTE E-MAILS FRA afsender@example.org.\n $help</div><p>Message</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
    }

    @Test
    fun `preserves another row in the same table`() {
        // Arrange
        val document = Jsoup.parse(
            "<table><tr><td>Du får ikke ofte e-mails fra afsender@example.org. $help</td></tr>" +
                "<tr><td><b>Selve mailen</b></td></tr></table>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.select("tr").size).isEqualTo(1)
        assertThat(document.selectFirst("b")!!.text()).isEqualTo("Selve mailen")
    }

    @Test
    fun `preserves content in the same cell`() {
        // Arrange
        val document = Jsoup.parse(
            "<table><tr><td>Du får ikke ofte e-mails fra afsender@example.org. $help" +
                "<p>Selve mailen</p></td></tr></table>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Selve mailen")
        assertThat(document.select("table").size).isEqualTo(1)
    }

    @Test
    fun `preserves text immediately after the help link in the same paragraph`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>Du får ikke ofte e-mails fra afsender@example.org. $help. Her er rapporten.</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Her er rapporten.")
    }

    @Test
    fun `removes text only header and preserves the following indented line`() {
        // Arrange
        val document = Jsoup.parse(
            "<pre>Du får ikke ofte e-mails fra afsender@example.org.\n    Her er rapporten.\nSidste linje.</pre>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.selectFirst("pre")!!.wholeText()).isEqualTo("    Her er rapporten.\nSidste linje.")
    }

    @Test
    fun `removes Danish help text without a hyperlink`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>Du får ikke ofte e-mails fra afsender@example.org. " +
                "Få mere at vide om, hvorfor dette er vigtigt.</p><p>Message</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
    }

    @Test
    fun `removes English help text without a hyperlink`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>You don't often get email from sender@example.org. Learn why this is important.</p><p>Message</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
    }

    @Test
    fun `removes banners inside quoted messages and preserves quote contents`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>Mit svar</p><blockquote><p>You don't often get email from sender@example.org. $help</p>" +
                "<p>Den oprindelige mail</p></blockquote>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Mit svar Den oprindelige mail")
        assertThat(document.selectFirst("blockquote")!!.text()).isEqualTo("Den oprindelige mail")
    }

    @Test
    fun `handles multiple consecutive notices`() {
        // Arrange
        val document = Jsoup.parse(
            "<div>Du får ikke ofte e-mails fra first@example.org. $help</div>" +
                "<div>You don't often get email from second@example.org. $help</div><p>Message</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
    }

    @Test
    fun `preserves normal prose mentioning the notice`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>Outlook viser teksten Du får ikke ofte e-mails fra sender@example.org. $help</p>",
        )
        val original = document.outerHtml()

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }

    @Test
    fun `preserves a similar sentence without a sender address`() {
        // Arrange
        val document = Jsoup.parse("<p>Du får ikke ofte e-mails fra os, men her er en opdatering.</p>")
        val original = document.outerHtml()

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }

    @Test
    fun `preserves unrelated help links and external sender notices`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>Denne mail kommer fra en ekstern afsender. Vær forsigtig.</p>" +
                "<p>Vejledning: $help</p>",
        )
        val original = document.outerHtml()

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }

    @Test
    fun `does not treat misleading or malformed help URLs as Microsoft links`() {
        listOf(
            "https://aka.ms.evil.example/LearnAboutSenderIdentification",
            "https://other.example/?url=https://aka.ms/LearnAboutSenderIdentification",
            "https://user@aka.ms/LearnAboutSenderIdentification",
            "https://aka.ms/OtherTopic",
            "https://aka.ms/%ZZ",
            "javascript:alert(1)",
        ).forEach { link ->
            // Arrange
            val document = Jsoup.parse(
                "<p>Du får ikke ofte e-mails fra sender@example.org. <a href='$link'>Læs mere</a></p>",
            )

            // Act
            testSubject.remove(document)

            // Assert
            assertThat(document.selectFirst("a")!!.attr("href")).isEqualTo(link)
            assertThat(document.body().text()).isEqualTo("Du får ikke ofte e-mails fra sender@example.org. Læs mere")
        }
    }

    @Test
    fun `accepts Microsoft help URL case differences query strings and fragments`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>Du får ikke ofte e-mails fra sender@example.org. " +
                "<a href='https://AKA.MS/learnaboutsenderidentification/?source=email#help'>Læs mere</a></p>" +
                "<p>Message</p>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.body().text()).isEqualTo("Message")
    }

    @Test
    fun `preserves inline images in the same container`() {
        // Arrange
        val document = Jsoup.parse(
            "<div>Du får ikke ofte e-mails fra sender@example.org. $help<img src='cid:chart'></div>",
        )

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.selectFirst("img")!!.attr("src")).isEqualTo("cid:chart")
    }

    @Test
    fun `preserves an ordinary sentence starting with similar wording and an address`() {
        // Arrange
        val document = Jsoup.parse(
            "<p>Du får ikke ofte e-mails fra mig@example.org, men jeg vil gerne dele rapporten.</p>",
        )
        val original = document.outerHtml()

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }

    @Test
    fun `removing twice has the same result as removing once`() {
        // Arrange
        val document = Jsoup.parse("<p>Du får ikke ofte e-mails fra sender@example.org. $help</p><p>Message</p>")
        testSubject.remove(document)
        val original = document.outerHtml()

        // Act
        testSubject.remove(document)

        // Assert
        assertThat(document.outerHtml()).isEqualTo(original)
    }
}
