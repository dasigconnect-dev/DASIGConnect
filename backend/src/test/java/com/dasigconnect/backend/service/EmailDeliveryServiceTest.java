package com.dasigconnect.backend.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.EmailDeliveryLogRepository;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock
    private EmailDeliveryLogRepository deliveryLogRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailDeliveryService emailDeliveryService;

    private User recipient() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("member@example.com");
        return user;
    }

    @Test
    void send_recipientWithEmailNotificationsOn_sends() {
        User user = recipient(); // notifyEmail defaults to true

        emailDeliveryService.send(user, "SUBMISSION_APPROVED", "Subject", "Body");

        verify(emailService).sendPlainText(eq("member@example.com"), eq("Subject"), eq("Body"));
        verify(deliveryLogRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    @Test
    void send_recipientWithEmailNotificationsOff_isSuppressed() {
        User user = recipient();
        user.setNotifyEmail(false);

        emailDeliveryService.send(user, "SUBMISSION_APPROVED", "Subject", "Body");

        verify(emailService, never()).sendPlainText(anyString(), anyString(), anyString());
        verify(deliveryLogRepository, never()).save(any());
    }
}
