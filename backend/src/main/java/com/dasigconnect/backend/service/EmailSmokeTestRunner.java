package com.dasigconnect.backend.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EmailSmokeTestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmailSmokeTestRunner.class);

    private final EmailService emailService;
    private final String smokeTestRecipient;

    public EmailSmokeTestRunner(
            EmailService emailService,
            @Value("${app.mail.smoke-test.to:}") String smokeTestRecipient) {
        this.emailService = emailService;
        this.smokeTestRecipient = smokeTestRecipient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (smokeTestRecipient == null || smokeTestRecipient.isBlank()) {
            return;
        }

        String recipient = smokeTestRecipient.trim();
        log.info("Sending email smoke-test message to {}", recipient);
        emailService.sendPlainText(
                recipient,
                "DASIGConnect email smoke test",
                "DASIGConnect email smoke test sent at " + Instant.now());
        log.info("Email smoke-test message sent to {}", recipient);
    }
}
