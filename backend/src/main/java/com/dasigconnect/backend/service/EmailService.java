package com.dasigconnect.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String appBaseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@dasigconnect.local}") String fromAddress,
            @Value("${app.frontend.base-url:http://localhost:5173}") String appBaseUrl,
            @Value("${spring.mail.host:localhost}") String mailHost,
            @Value("${spring.mail.port:2525}") int mailPort,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean smtpAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean startTlsEnabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.appBaseUrl = appBaseUrl;
        log.info("Email service configured with SMTP {}:{}, username={}, from={}, auth={}, starttls={}",
                mailHost,
                mailPort,
                blankToPlaceholder(mailUsername),
                fromAddress,
                smtpAuth,
                startTlsEnabled);
    }

    public void sendInvitationEmail(String to, String token) {
        String link = buildInvitationLink(token);
        sendHtml(
                to,
                "You're invited to DASIGConnect",
                "<p>You have been invited to DASIGConnect.</p><p><a href=\"" + link + "\">Accept invitation</a></p>",
                "You have been invited to DASIGConnect.\n\nAccept your invitation: " + link);
    }

    public String buildInvitationLink(String token) {
        return appBaseUrl.replaceAll("/$", "") + "/invite?token=" + token;
    }

    public void sendPasswordResetEmail(String to, String token) {
        String link = appBaseUrl + "/forgot-password/reset?token=" + token;
        sendHtml(
                to,
                "Reset your DASIGConnect password",
                "<p>Use this link to reset your DASIGConnect password:</p><p><a href=\"" + link + "\">Reset password</a></p>",
                "Use this link to reset your DASIGConnect password:\n\n" + link);
    }

    public void sendPlainText(String to, String subject, String body) {
        retry(() -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        });
    }

    public void sendHtml(String to, String subject, String htmlBody, String fallbackText) {
        retry(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromAddress);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(fallbackText, htmlBody);
                mailSender.send(message);
            } catch (MessagingException ex) {
                throw new IllegalStateException("Unable to create email message", ex);
            }
        });
    }

    private void retry(Runnable sendOperation) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                sendOperation.run();
                return;
            } catch (MailException | IllegalStateException ex) {
                lastFailure = ex;
            }
        }
        String failureMessage = rootCauseMessage(lastFailure);
        log.warn("Email delivery failed after {} attempts: {}", MAX_ATTEMPTS, failureMessage, lastFailure);
        throw new IllegalStateException(
                "Email delivery failed after " + MAX_ATTEMPTS + " attempts: " + failureMessage,
                lastFailure);
    }

    private static String blankToPlaceholder(String value) {
        return value == null || value.isBlank() ? "<empty>" : value;
    }

    private static String rootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return cursor.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
