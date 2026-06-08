package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;
import com.dasigconnect.backend.model.entity.NotificationEventType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.MediaIntegrityTarget;
import com.dasigconnect.backend.repository.NotificationRepository;
import com.dasigconnect.backend.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaIntegrityAlertService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public MediaIntegrityAlertService(UserRepository userRepository,
                                      NotificationRepository notificationRepository,
                                      NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void notifyFailure(MediaIntegrityTarget target, MediaIntegrityStatus status) {
        Map<UUID, User> recipients = new LinkedHashMap<>();
        userRepository.findByInstitutionIdAndRoleAndAccountStateOrderByCreatedAtDesc(
                        target.institutionId(), UserRole.validator, UserStatus.active)
                .forEach(user -> recipients.put(user.getId(), user));
        userRepository.findByRoleAndAccountState(UserRole.administrator, UserStatus.active)
                .forEach(user -> recipients.put(user.getId(), user));

        String deepLink = "/media-repository?assetId=" + target.assetId();
        Instant dedupeSince = Instant.now().minus(24, ChronoUnit.HOURS);
        String message = message(target.assetCode(), status);
        for (User recipient : recipients.values()) {
            boolean duplicate = notificationRepository
                    .existsByRecipientIdAndEventTypeAndDeepLinkAndCreatedAtAfter(
                            recipient.getId(),
                            NotificationEventType.media_integrity_failed,
                            deepLink,
                            dedupeSince);
            if (!duplicate) {
                notificationService.createNotification(
                        recipient,
                        NotificationEventType.media_integrity_failed,
                        message,
                        deepLink);
            }
        }
    }

    private String message(String assetCode, MediaIntegrityStatus status) {
        String code = assetCode == null || assetCode.isBlank() ? "A media asset" : assetCode;
        return switch (status) {
            case MISMATCH -> code + " no longer matches its stored SHA-256 checksum.";
            case MISSING -> code + " is missing from storage.";
            case ERROR -> code + " could not be checked because storage verification failed.";
            default -> code + " requires an integrity review.";
        };
    }
}
