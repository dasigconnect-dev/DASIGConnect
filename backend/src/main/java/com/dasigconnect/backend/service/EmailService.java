package com.dasigconnect.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Year;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;
    private final String replyToAddress;
    private final String appBaseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@dasigconnect.local}") String fromAddress,
            @Value("${app.mail.from.name:DASIGConnect}") String fromName,
            @Value("${app.mail.reply-to:${app.mail.from:no-reply@dasigconnect.local}}") String replyToAddress,
            @Value("${app.frontend.base-url:http://localhost:5173}") String appBaseUrl,
            @Value("${spring.mail.host:localhost}") String mailHost,
            @Value("${spring.mail.port:2525}") int mailPort,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean smtpAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean startTlsEnabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.replyToAddress = replyToAddress;
        this.appBaseUrl = appBaseUrl;
        log.info("Email service configured with SMTP {}:{}, username={}, from={}, auth={}, starttls={}",
                mailHost,
                mailPort,
                blankToPlaceholder(mailUsername),
                fromAddress,
                smtpAuth,
                startTlsEnabled);
        if (isLocalhostUrl(appBaseUrl)) {
            log.warn("Email links are configured with a localhost frontend URL ({}). Use a public HTTPS APP_FRONTEND_BASE_URL before testing institutional delivery.", appBaseUrl);
        }
    }

    public void sendInvitationEmail(String to, String token) {
        String link = buildInvitationLink(token);
        String escapedLink = escapeHtml(link);
        sendHtml(
                to,
                "DASIGConnect invitation",
                """
                        <!doctype html>
                        <html lang="en">
                        <head>
                          <meta charset="UTF-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1.0">
                          <title>DASIGConnect invitation</title>
                        </head>
                        <body style="margin:0;padding:0;background:#f6f8fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f6f8fb;padding:24px 0;">
                            <tr>
                              <td align="center">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border:1px solid #d9e2ef;border-radius:8px;">
                                  <tr>
                                    <td style="padding:28px 32px 12px 32px;">
                                      <h1 style="font-size:22px;line-height:1.3;margin:0;color:#10234d;">You have been invited to DASIGConnect</h1>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:0 32px 18px 32px;font-size:15px;line-height:1.6;color:#33415c;">
                                      An administrator invited you to join DASIGConnect. Use the button below to set up your account.
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:0 32px 24px 32px;">
                                      <a href="%s" style="display:inline-block;background:#1a73e8;color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;border-radius:6px;padding:12px 18px;">Accept invitation</a>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:0 32px 28px 32px;font-size:13px;line-height:1.5;color:#5b6b84;">
                                      If the button does not work, copy and paste this link into your browser:<br>
                                      <a href="%s" style="color:#1a73e8;word-break:break-all;">%s</a>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="border-top:1px solid #e5ecf5;padding:16px 32px 24px 32px;font-size:12px;line-height:1.5;color:#718096;">
                                      This is a transactional message from DASIGConnect. If you did not expect this invitation, you can ignore this email.
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                          </table>
                        </body>
                        </html>
                        """.formatted(escapedLink, escapedLink, escapedLink),
                "You have been invited to DASIGConnect.\n\n"
                        + "Accept your invitation: " + link + "\n\n"
                        + "If you did not expect this invitation, you can ignore this email.");
    }

    public String buildInvitationLink(String token) {
        return appBaseUrl.replaceAll("/$", "") + "/invite?token=" + token;
    }

    public void sendPasswordResetEmail(String to, String token) {
        String link = appBaseUrl.replaceAll("/$", "") + "/forgot-password/reset?token=" + token;
        String escapedLink = escapeHtml(link);
        sendHtml(
                to,
                "DASIGConnect password reset",
                """
                        <!doctype html>
                        <html lang="en">
                        <head>
                          <meta charset="UTF-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1.0">
                          <title>DASIGConnect password reset</title>
                        </head>
                        <body style="margin:0;padding:0;background:#f6f8fb;font-family:Arial,Helvetica,sans-serif;color:#172033;">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f6f8fb;padding:24px 0;">
                            <tr>
                              <td align="center">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border:1px solid #d9e2ef;border-radius:8px;">
                                  <tr>
                                    <td style="padding:28px 32px 12px 32px;">
                                      <h1 style="font-size:22px;line-height:1.3;margin:0;color:#10234d;">Reset your DASIGConnect password</h1>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:0 32px 18px 32px;font-size:15px;line-height:1.6;color:#33415c;">
                                      Use the button below to choose a new password for your DASIGConnect account.
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:0 32px 24px 32px;">
                                      <a href="%s" style="display:inline-block;background:#1a73e8;color:#ffffff;text-decoration:none;font-weight:700;font-size:15px;border-radius:6px;padding:12px 18px;">Reset password</a>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:0 32px 28px 32px;font-size:13px;line-height:1.5;color:#5b6b84;">
                                      If the button does not work, copy and paste this link into your browser:<br>
                                      <a href="%s" style="color:#1a73e8;word-break:break-all;">%s</a>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="border-top:1px solid #e5ecf5;padding:16px 32px 24px 32px;font-size:12px;line-height:1.5;color:#718096;">
                                      This is a transactional message from DASIGConnect. If you did not request this reset, you can ignore this email.
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                          </table>
                        </body>
                        </html>
                        """.formatted(escapedLink, escapedLink, escapedLink),
                "Use this link to reset your DASIGConnect password:\n\n"
                        + link + "\n\n"
                        + "If you did not request this reset, you can ignore this email.");
    }

    public void sendPlainText(String to, String subject, String body) {
        retry(() -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setReplyTo(replyToAddress);
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
                helper.setFrom(new InternetAddress(fromAddress, fromName, "UTF-8"));
                helper.setReplyTo(new InternetAddress(replyToAddress, fromName, "UTF-8"));
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(fallbackText, htmlBody);
                message.setHeader("Auto-Submitted", "auto-generated");
                message.setHeader("X-Auto-Response-Suppress", "All");
                message.setHeader("X-Entity-Ref-ID", "dasigconnect-" + Year.now().getValue());
                mailSender.send(message);
            } catch (MessagingException | UnsupportedEncodingException ex) {
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

    private static boolean isLocalhostUrl(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("[::1]");
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}