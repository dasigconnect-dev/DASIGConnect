package com.dasigconnect.backend.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.EmailDeliveryLog;
import com.dasigconnect.backend.model.entity.EmailDeliveryStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.EmailDeliveryLogRepository;

@Service
@Transactional
public class EmailDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryService.class);
    private static final int ERROR_DETAIL_MAX = 1000;

    private final EmailDeliveryLogRepository deliveryLogRepository;
    private final EmailService emailService;

    public EmailDeliveryService(EmailDeliveryLogRepository deliveryLogRepository, EmailService emailService) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.emailService = emailService;
    }

    /**
     * Sends a plain-text email and records the attempt in email_delivery_log.
     * Never throws — a delivery failure is logged and recorded as failed.
     */
    public void send(User recipient, String templateCode, String subject, String body) {
        EmailDeliveryLog record = new EmailDeliveryLog();
        record.setRecipient(recipient);
        record.setTemplateCode(templateCode);
        record.setStatus(EmailDeliveryStatus.queued);
        deliveryLogRepository.save(record);

        try {
            emailService.sendPlainText(recipient.getEmail(), subject, body);
            record.setStatus(EmailDeliveryStatus.sent);
            record.setDeliveredAt(Instant.now());
        } catch (Exception ex) {
            logger.warn("Email delivery failed for {} [{}]: {}", recipient.getEmail(), templateCode, ex.getMessage());
            record.setStatus(EmailDeliveryStatus.failed);
            record.setErrorDetail(truncate(ex.getMessage()));
        }
        deliveryLogRepository.save(record);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_DETAIL_MAX ? s : s.substring(0, ERROR_DETAIL_MAX);
    }
}
