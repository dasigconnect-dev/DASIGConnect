package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dasigconnect.backend.external.MailTransport;
import com.dasigconnect.backend.external.MailTransportException;

class EmailServiceTest {

    @Test
    void sendInvitationEmail_buildsHtmlAndPlainTextWithLink() {
        CapturingMailTransport transport = new CapturingMailTransport();
        EmailService emailService = new EmailService(transport, "https://dasigconnect.example");

        emailService.sendInvitationEmail("recipient@cit.edu", "abc123");

        assertThat(transport.to).isEqualTo("recipient@cit.edu");
        assertThat(transport.subject).isEqualTo("DASIGConnect invitation");
        assertThat(transport.html).contains("Accept invitation");
        assertThat(transport.html).contains("https://dasigconnect.example/invite?token=abc123");
        assertThat(transport.html).doesNotContain("localhost");
        assertThat(transport.text).contains("Accept your invitation");
        assertThat(transport.text).contains("https://dasigconnect.example/invite?token=abc123");
        assertThat(transport.headers).containsEntry("Auto-Submitted", "auto-generated");
        assertThat(transport.headers).containsEntry("X-Auto-Response-Suppress", "All");
    }

    @Test
    void sendPasswordResetEmail_buildsResetLink() {
        CapturingMailTransport transport = new CapturingMailTransport();
        EmailService emailService = new EmailService(transport, "https://dasigconnect.example/");

        emailService.sendPasswordResetEmail("user@cit.edu", "tok-9");

        assertThat(transport.subject).isEqualTo("DASIGConnect password reset");
        assertThat(transport.html).contains("https://dasigconnect.example/forgot-password/reset?token=tok-9");
        assertThat(transport.text).contains("https://dasigconnect.example/forgot-password/reset?token=tok-9");
    }

    @Test
    void sendPlainText_retriesThenSurfacesFailure() {
        MailTransport alwaysFails = new MailTransport("", "https://api.resend.com", "no-reply@x", "DASIGConnect", "no-reply@x") {
            @Override
            public void send(String to, String subject, String html, String text, Map<String, String> headers) {
                throw new MailTransportException("boom");
            }
        };
        EmailService emailService = new EmailService(alwaysFails, "https://dasigconnect.example");

        assertThatThrownBy(() -> emailService.sendPlainText("user@cit.edu", "s", "b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email delivery failed after 3 attempts");
    }

    private static class CapturingMailTransport extends MailTransport {

        private String to;
        private String subject;
        private String html;
        private String text;
        private Map<String, String> headers = new LinkedHashMap<>();

        CapturingMailTransport() {
            super("test-key", "https://api.resend.com", "no-reply@dasigconnect.local", "DASIGConnect",
                    "no-reply@dasigconnect.local");
        }

        @Override
        public void send(String to, String subject, String html, String text, Map<String, String> headers) {
            this.to = to;
            this.subject = subject;
            this.html = html;
            this.text = text;
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        }
    }
}
