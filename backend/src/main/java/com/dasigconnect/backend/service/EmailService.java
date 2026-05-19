package com.dasigconnect.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final int MAX_ATTEMPTS = 3;

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String appBaseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@dasigconnect.local}") String fromAddress,
            @Value("${app.frontend.base-url:http://localhost:5173}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.appBaseUrl = appBaseUrl;
    }

    public void sendInvitationEmail(String to, String token) {
        String link = appBaseUrl + "/invite/accept?token=" + token;
        sendHtml(
                to,
                "You're invited to DASIGConnect",
                "<p>You have been invited to DASIGConnect.</p><p><a href=\"" + link + "\">Accept invitation</a></p>",
                "You have been invited to DASIGConnect.\n\nAccept your invitation: " + link);
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
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
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
        throw lastFailure;
    }
}
