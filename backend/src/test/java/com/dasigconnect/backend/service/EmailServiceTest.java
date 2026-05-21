package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTest {

    @Test
    void sendInvitationEmail_buildsCompliantMultipartMessage() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        EmailService emailService = new EmailService(
                mailSender,
                "dasigconnect.dev@gmail.com",
                "DASIGConnect",
                "dasigconnect.dev@gmail.com",
                "https://dasigconnect.example",
                "smtp.gmail.com",
                587,
                "dasigconnect.dev@gmail.com",
                true,
                true);

        emailService.sendInvitationEmail("recipient@cit.edu", "abc123");

        MimeMessage message = mailSender.lastMessage;
        assertThat(message).isNotNull();
        assertThat(message.getSubject()).isEqualTo("DASIGConnect invitation");
        assertThat(message.getContentType()).containsIgnoringCase("multipart");
        assertThat(message.getHeader("Auto-Submitted", null)).isEqualTo("auto-generated");
        assertThat(message.getHeader("X-Auto-Response-Suppress", null)).isEqualTo("All");
        assertThat(extractText(message, "text/plain")).contains("Accept your invitation");
        assertThat(extractText(message, "text/html")).contains("Accept invitation");
        assertThat(extractText(message, "text/html")).contains("https://dasigconnect.example/invite?token=abc123");
        assertThat(extractText(message, "text/html")).doesNotContain("localhost");

        Address from = message.getFrom()[0];
        assertThat(from).isInstanceOf(InternetAddress.class);
        InternetAddress fromAddress = (InternetAddress) from;
        assertThat(fromAddress.getAddress()).isEqualTo("dasigconnect.dev@gmail.com");
        assertThat(fromAddress.getPersonal()).isEqualTo("DASIGConnect");
        assertThat(message.getReplyTo()[0].toString()).contains("dasigconnect.dev@gmail.com");
    }

    private static String extractText(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return String.valueOf(part.getContent());
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                result.append(extractText(multipart.getBodyPart(i), mimeType));
            }
            return result.toString();
        }
        return "";
    }

    private static class CapturingMailSender implements JavaMailSender {

        private MimeMessage lastMessage;

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), contentStream);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            try {
                mimeMessage.saveChanges();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            this.lastMessage = mimeMessage;
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            this.lastMessage = mimeMessages[mimeMessages.length - 1];
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator mimeMessagePreparator) {
            MimeMessage message = createMimeMessage();
            try {
                mimeMessagePreparator.prepare(message);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            this.lastMessage = message;
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator... mimeMessagePreparators) {
            for (org.springframework.mail.javamail.MimeMessagePreparator preparator : mimeMessagePreparators) {
                send(preparator);
            }
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
        }
    }
}
