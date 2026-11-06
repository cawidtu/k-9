package app.k9mail.html.cleaner

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.jsoup.Jsoup
import org.junit.Test

class OutlookMessageCleanerTest {
    private val testSubject = OutlookMessageCleaner()

    @Test
    fun `plain text cleanup removes a bracketed notice before preview generation`() {
        // Arrange
        val text = "[Du får ikke ofte mails fra sender@example.org " +
            "Få mere at vide om, hvorfor dette er vigtigt, på " +
            "https://aka.ms/LearnAboutSenderIdentification ]\n\nHer er rapporten."

        // Act
        val result = testSubject.cleanPlainText(text)

        // Assert
        assertThat(result).isEqualTo("\nHer er rapporten.")
    }

    @Test
    fun `HTML cleanup removes a notice whose label does not expose the help URL`() {
        // Arrange
        val html = "<table><tr><td>You don't often get email from sender@example.org. " +
            "<a href='https://aka.ms/LearnAboutSenderIdentification'>Learn more</a></td></tr></table>" +
            "<p>Message</p>"

        // Act
        val result = testSubject.cleanHtml(html)

        // Assert
        assertThat(Jsoup.parse(result).body().text()).isEqualTo("Message")
    }

    @Test
    fun `plain text cleanup restores Safe Links without interpreting text as HTML`() {
        // Arrange
        val text = "<b>Literal text</b> https://eu.safelinks.protection.outlook.com/" +
            "?url=https%3A%2F%2Fexample.org%2Freport&data=test"

        // Act
        val result = testSubject.cleanPlainText(text)

        // Assert
        assertThat(result).isEqualTo("<b>Literal text</b> https://example.org/report")
    }

    @Test
    fun `cached Danish preview removes its notice and collapsed URL placeholder`() {
        // Arrange
        val preview = "[Du får ikke ofte mails fra sender@example.org " +
            "Få mere at vide om, hvorfor dette er vigtigt, på ... ] Her er rapporten."

        // Act
        val result = testSubject.cleanCachedPreview(preview)

        // Assert
        assertThat(result).isEqualTo("Her er rapporten.")
    }

    @Test
    fun `cached English preview removes a bracketed notice with an ellipsis URL`() {
        // Arrange
        val preview = "[You don't often get email from sender@example.org. " +
            "Learn why this is important at … ] Actual message"

        // Act
        val result = testSubject.cleanCachedPreview(preview)

        // Assert
        assertThat(result).isEqualTo("Actual message")
    }

    @Test
    fun `cached HTML based preview removes an unbracketed notice`() {
        // Arrange
        val preview = "Du får ikke ofte mails fra sender@example.org. " +
            "Få mere at vide om, hvorfor dette er vigtigt Dear recipient, here is the report."

        // Act
        val result = testSubject.cleanCachedPreview(preview)

        // Assert
        assertThat(result).isEqualTo("Dear recipient, here is the report.")
    }

    @Test
    fun `cached ordinary text retains markup punctuation and URL placeholders`() {
        // Arrange
        val preview = "Et <b>eksempel</b> [se rapporten på ... ] og en fortsættelse…"

        // Act
        val result = testSubject.cleanCachedPreview(preview)

        // Assert
        assertThat(result).isEqualTo(preview)
    }

    @Test
    fun `cached ordinary prose containing notice wording is preserved`() {
        // Arrange
        val preview = "Outlook viser: Du får ikke ofte mails fra sender@example.org. Det er et eksempel."

        // Act
        val result = testSubject.cleanCachedPreview(preview)

        // Assert
        assertThat(result).isEqualTo(preview)
    }

    @Test
    fun `cached notice truncated during the help text produces an empty preview`() {
        // Arrange
        val preview = "[Du får ikke ofte mails fra sender@example.org Få mere at vide om, hvorfor dette…"

        // Act
        val result = testSubject.cleanCachedPreview(preview)

        // Assert
        assertThat(result).isEqualTo("")
    }

    @Test
    fun `cached cleanup preserves an ordinary bracketed email sentence`() {
        // Arrange
        val preview = "[Du får ikke ofte mails fra mig@example.org, men rapporten findes på ... ] Mere tekst"

        // Act
        val result = testSubject.cleanCachedPreview(preview)

        // Assert
        assertThat(result).isEqualTo(preview)
    }
}
