package com.fsck.k9.mailstore

import app.k9mail.html.cleaner.HtmlHeadProvider
import app.k9mail.html.cleaner.HtmlProcessor
import app.k9mail.legacy.di.DI
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.TestCoreResourceProvider
import com.fsck.k9.mail.internet.MimeMessage
import com.fsck.k9.message.extractors.AttachmentInfoExtractor
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.jsoup.Jsoup
import org.junit.Before
import org.junit.Test

class MessageViewInfoExtractorSafeLinksTest : K9RobolectricTest() {
    private lateinit var testSubject: MessageViewInfoExtractor

    @Before
    fun setUp() {
        Log.logger = TestLogger()
        val processor = HtmlProcessor(
            customClasses = emptySet(),
            htmlHeadProvider = object : HtmlHeadProvider {
                override val headHtml = ""
            },
        )
        testSubject = MessageViewInfoExtractor(
            DI.get(AttachmentInfoExtractor::class.java),
            processor,
            TestCoreResourceProvider(),
        )
    }

    @Test
    fun `plain message is cleaned in both selectable display formats`() {
        // Arrange
        val message = MimeMessage.parseMimeMessage(
            (
                "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8; format=flowed\r\n" +
                    "Content-Transfer-Encoding: 8bit\r\n\r\n" +
                    "[Du får ikke ofte mails fra sender@example.org " +
                    "Få mere at vide om, hvorfor dette er vigtigt, på " +
                    "https://aka.ms/LearnAboutSenderIdentification ]\r\n\r\n" +
                    "Message: https://eu.safelinks.protection.outlook.com/" +
                    "?url=https%3A%2F%2Fexample.org%2Freport&data=test"
                ).byteInputStream(),
            false,
        )

        // Act
        val result = testSubject.extractMessageForView(message, null, false)

        // Assert
        assertThat(Jsoup.parse(result.text).body().text()).isEqualTo("Message: https://example.org/report")
        assertThat(Jsoup.parse(result.textPlainFormatted).body().text())
            .isEqualTo("Message: https://example.org/report")
        assertThat(result.textPlainFormatted.contains("<html")).isEqualTo(false)
        assertThat(result.textPlainFormatted.contains("<head")).isEqualTo(false)
    }

    @Test
    fun `multipart alternative text view is cleaned independently of the HTML alternative`() {
        // Arrange
        val source = """
            MIME-Version: 1.0
            Content-Type: multipart/alternative; boundary="message-boundary"

            --message-boundary
            Content-Type: text/plain; charset=UTF-8
            Content-Transfer-Encoding: 8bit

            Du får ikke ofte mails fra sender@example.org. Få mere at vide om, hvorfor dette er vigtigt<https://aka.ms/LearnAboutSenderIdentification>

            Plain alternative
            --message-boundary
            Content-Type: text/html; charset=UTF-8
            Content-Transfer-Encoding: 8bit

            <p>Du får ikke ofte mails fra sender@example.org. <a href="https://aka.ms/LearnAboutSenderIdentification">Læs mere</a></p><p>HTML alternative</p>
            --message-boundary--
        """.trimIndent()
        val message = MimeMessage.parseMimeMessage(source.byteInputStream(), false)

        // Act
        val result = testSubject.extractMessageForView(message, null, false)

        // Assert
        assertThat(Jsoup.parse(result.text).body().text()).isEqualTo("HTML alternative")
        assertThat(Jsoup.parse(result.textPlainFormatted).body().text()).isEqualTo("Plain alternative")
    }
}
