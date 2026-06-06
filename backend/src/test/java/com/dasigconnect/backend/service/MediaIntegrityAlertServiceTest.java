package com.dasigconnect.backend.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.MediaIntegrityTarget;
import com.dasigconnect.backend.repository.NotificationRepository;
import com.dasigconnect.backend.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaIntegrityAlertServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationService notificationService;

    private MediaIntegrityAlertService service;

    @BeforeEach
    void setUp() {
        service = new MediaIntegrityAlertService(
                userRepository, notificationRepository, notificationService);
    }

    @Test
    void notifyFailure_alertsActiveValidatorAndAdministrator() {
        UUID institutionId = UUID.randomUUID();
        User validator = user(UserRole.validator);
        User administrator = user(UserRole.administrator);
        MediaIntegrityTarget target = new MediaIntegrityTarget(
                UUID.randomUUID(),
                institutionId,
                "https://storage.example/missing.jpg",
                "AST-404");
        when(userRepository.findByInstitutionIdAndRoleAndAccountStateOrderByCreatedAtDesc(
                institutionId, UserRole.validator, UserStatus.active))
                .thenReturn(List.of(validator));
        when(userRepository.findByRoleAndAccountState(UserRole.administrator, UserStatus.active))
                .thenReturn(List.of(administrator));

        service.notifyFailure(target, MediaIntegrityStatus.MISSING);

        verify(notificationService).createNotification(
                validator,
                NotificationEventType.media_integrity_failed,
                "AST-404 is missing from storage.",
                "/media-repository?assetId=" + target.assetId());
        verify(notificationService).createNotification(
                administrator,
                NotificationEventType.media_integrity_failed,
                "AST-404 is missing from storage.",
                "/media-repository?assetId=" + target.assetId());
    }

    @Test
    void notifyFailure_recentDuplicateIsNotSentAgain() {
        UUID institutionId = UUID.randomUUID();
        User validator = user(UserRole.validator);
        MediaIntegrityTarget target = new MediaIntegrityTarget(
                UUID.randomUUID(),
                institutionId,
                "https://storage.example/missing.jpg",
                "AST-404");
        String deepLink = "/media-repository?assetId=" + target.assetId();
        when(userRepository.findByInstitutionIdAndRoleAndAccountStateOrderByCreatedAtDesc(
                institutionId, UserRole.validator, UserStatus.active))
                .thenReturn(List.of(validator));
        when(userRepository.findByRoleAndAccountState(UserRole.administrator, UserStatus.active))
                .thenReturn(List.of());
        when(notificationRepository.existsByRecipientIdAndEventTypeAndDeepLinkAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(validator.getId()),
                org.mockito.ArgumentMatchers.eq(NotificationEventType.media_integrity_failed),
                org.mockito.ArgumentMatchers.eq(deepLink),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(true);

        service.notifyFailure(target, MediaIntegrityStatus.MISSING);

        verify(notificationService, never()).createNotification(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private User user(UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setAccountState(UserStatus.active);
        return user;
    }
}
