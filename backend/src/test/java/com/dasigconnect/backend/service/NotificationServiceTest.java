package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.notification.NotificationDto;
import com.dasigconnect.backend.model.entity.Notification;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.NotificationRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private JwtUserDetails userPrincipal;
    private User recipient;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userPrincipal = new JwtUserDetails(userId, "user@example.com", "contributor", UUID.randomUUID());
        recipient = new User();
        recipient.setId(userId);
    }

    @Test
    void list_returnsNotificationDtos() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(recipient);
        notification.setEventType(NotificationEventType.submission_pending);
        notification.setMessage("New submission pending review.");
        notification.setDeepLink("/dashboard");

        when(notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(notification));

        List<NotificationDto> result = notificationService.list(userPrincipal);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("submission_pending");
        assertThat(result.get(0).getMessage()).isEqualTo("New submission pending review.");
    }

    @Test
    void unreadCount_delegatesToRepository() {
        when(notificationRepository.countByRecipientIdAndReadAtIsNull(userId)).thenReturn(3L);

        long count = notificationService.unreadCount(userPrincipal);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void markRead_updatesReadAtWhenOwner() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(recipient);
        notification.setEventType(NotificationEventType.generic);

        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        notificationService.markRead(notification.getId(), userPrincipal);

        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_otherUser_returnsNotFound() {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        User otherRecipient = new User();
        otherRecipient.setId(UUID.randomUUID());
        notification.setRecipient(otherRecipient);
        notification.setEventType(NotificationEventType.generic);

        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markRead(notification.getId(), userPrincipal))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markAllRead_marksAllForUser() {
        notificationService.markAllRead(userPrincipal);

        verify(notificationRepository).markAllRead(eq(userId), any(Instant.class));
    }
}
