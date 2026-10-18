package com.dasigconnect.backend.service;

import com.dasigconnect.backend.exception.SubscriptionLimitExceededException;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionActionReservation;
import com.dasigconnect.backend.model.entity.SubscriptionActionReservationStatus;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import com.dasigconnect.backend.model.entity.SubscriptionUsageUnit;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.SubscriptionActionReservationRepository;
import com.dasigconnect.backend.repository.SubscriptionUsageEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionEntitlementService {

    private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Manila");

    private final SubscriptionActionReservationRepository reservationRepository;
    private final SubscriptionUsageEventRepository usageRepository;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubmissionRepository submissionRepository;
    private final boolean enforcementEnabled;
    private final Duration reservationTtl;

    public SubscriptionEntitlementService(
            SubscriptionActionReservationRepository reservationRepository,
            SubscriptionUsageEventRepository usageRepository,
            OrganizationSubscriptionRepository subscriptionRepository,
            SubmissionRepository submissionRepository,
            @Value("${app.subscription.enforcement-enabled:false}") boolean enforcementEnabled,
            @Value("${app.subscription.action-reservation-ttl-minutes:15}") long reservationTtlMinutes) {
        this.reservationRepository = reservationRepository;
        this.usageRepository = usageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.submissionRepository = submissionRepository;
        this.enforcementEnabled = enforcementEnabled;
        this.reservationTtl = Duration.ofMinutes(Math.max(reservationTtlMinutes, 1));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActionReservation reserveForSubmission(
            UUID submissionId,
            UUID userId,
            SubscriptionUsageEventType eventType,
            String requestKey,
            String sourceType) {
        var submission = submissionRepository.findByIdWithInstitution(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found."));
        return reserve(
                submission.getInstitution().getOrganizationId(),
                submission.getInstitution().getId(),
                userId,
                eventType,
                normalizedActionKey(eventType, requestKey, submissionId),
                sourceType,
                submissionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(UUID reservationId) {
        SubscriptionActionReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Subscription action reservation was not found."));
        if (reservation.getStatus() == SubscriptionActionReservationStatus.CONSUMED) {
            return;
        }
        if (reservation.getStatus() != SubscriptionActionReservationStatus.RESERVED
                || !reservation.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Subscription action reservation is no longer active.");
        }

        usageRepository.insertIfAbsent(
                UUID.randomUUID(),
                reservation.getOrganizationId(),
                reservation.getInstitutionId(),
                reservation.getUserId(),
                reservation.getEventType().name(),
                reservation.getQuantity(),
                SubscriptionUsageUnit.COUNT.name(),
                reservation.getIdempotencyKey(),
                reservation.getSourceType(),
                reservation.getSourceId(),
                Instant.now());
        reservation.setStatus(SubscriptionActionReservationStatus.CONSUMED);
        reservation.setConsumedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID reservationId) {
        if (reservationId == null) return;
        reservationRepository.findByIdForUpdate(reservationId).ifPresent(reservation -> {
            if (reservation.getStatus() == SubscriptionActionReservationStatus.RESERVED) {
                reservation.setStatus(SubscriptionActionReservationStatus.RELEASED);
                reservation.setReleasedAt(Instant.now());
            }
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanCreateInstitution(UUID organizationId) {
        if (!enforcementEnabled) return;
        OrganizationSubscription subscription = lockSubscription(organizationId);
        long used = usageRepository.currentInstitutionCount(organizationId);
        assertCapacity(
                "institutions", used, 1, subscription.getPlan().getInstitutionLimit(),
                subscription.getCurrentPeriodEnd());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanCommitSeat(UUID organizationId, String email) {
        if (!enforcementEnabled) return;
        OrganizationSubscription subscription = lockSubscription(organizationId);
        Instant now = Instant.now();
        long used = usageRepository.currentSeatCommitmentCount(organizationId, now);
        long requested = usageRepository.hasSeatCommitment(organizationId, email, now) ? 0 : 1;
        assertCapacity(
                "activeUserSeats", used, requested, subscription.getPlan().getActiveUserLimit(),
                subscription.getCurrentPeriodEnd());
    }

    private ActionReservation reserve(
            UUID organizationId,
            UUID institutionId,
            UUID userId,
            SubscriptionUsageEventType eventType,
            String idempotencyKey,
            String sourceType,
            UUID sourceId) {
        if (eventType != SubscriptionUsageEventType.SUBMISSION
                && eventType != SubscriptionUsageEventType.AI_CAPTION
                && eventType != SubscriptionUsageEventType.AI_MEDIA_SUGGESTION) {
            throw new IllegalArgumentException("Unsupported subscription action type: " + eventType);
        }

        OrganizationSubscription subscription = lockSubscription(organizationId);
        Instant now = Instant.now();
        reservationRepository.expireStaleReservations(organizationId, now);
        Optional<SubscriptionActionReservation> existing =
                reservationRepository.findByIdempotencyKeyForUpdate(idempotencyKey);

        Instant periodStart = subscription.getCurrentPeriodStart();
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        long used = usageRepository.sumConsumption(
                organizationId, eventType.name(), periodStart, periodEnd);
        long reserved = reservationRepository.sumActiveOrganizationReservations(
                organizationId, eventType, periodStart, periodEnd, now);
        long monthlyLimit = monthlyLimit(subscription.getPlan(), eventType);

        long dailyUsed = 0;
        long dailyReserved = 0;
        long dailyLimit = Long.MAX_VALUE;
        Instant dailyReset = periodEnd;
        if (eventType == SubscriptionUsageEventType.AI_CAPTION && userId != null) {
            LocalDate date = LocalDate.now(BILLING_ZONE);
            Instant dayStart = date.atStartOfDay(BILLING_ZONE).toInstant();
            dailyReset = date.plusDays(1).atStartOfDay(BILLING_ZONE).toInstant();
            dailyUsed = usageRepository.sumUserConsumption(
                    organizationId, userId, eventType.name(), dayStart, dailyReset);
            dailyReserved = reservationRepository.sumActiveUserReservations(
                    organizationId, userId, eventType, dayStart, dailyReset, now);
            dailyLimit = subscription.getPlan().getPerUserDailyCaptionLimit();
        }

        if (existing.isPresent()) {
            SubscriptionActionReservation reservation = existing.get();
            assertSameScope(reservation, organizationId, institutionId, userId, eventType, sourceId);
            if (reservation.getStatus() == SubscriptionActionReservationStatus.CONSUMED
                    || (reservation.getStatus() == SubscriptionActionReservationStatus.RESERVED
                        && reservation.getExpiresAt().isAfter(now))) {
                long monthlyRemaining = monthlyLimit - used - reserved;
                long dailyRemaining = dailyLimit == Long.MAX_VALUE
                        ? Long.MAX_VALUE : dailyLimit - dailyUsed - dailyReserved;
                boolean warning = monthlyRemaining <= Math.max(1, monthlyLimit / 5)
                        || (dailyLimit != Long.MAX_VALUE
                            && dailyRemaining <= Math.max(1, dailyLimit / 5));
                Instant resetAt = dailyRemaining < monthlyRemaining ? dailyReset : periodEnd;
                return result(reservation, Math.min(monthlyRemaining, dailyRemaining), resetAt, warning);
            }
        }

        boolean monthlyExceeded = used + reserved + 1 > monthlyLimit;
        boolean dailyExceeded = dailyUsed + dailyReserved + 1 > dailyLimit;
        if (enforcementEnabled && monthlyExceeded) {
            throw new SubscriptionLimitExceededException(
                    eventType == SubscriptionUsageEventType.AI_CAPTION ? "aiCaptions"
                            : eventType == SubscriptionUsageEventType.AI_MEDIA_SUGGESTION
                                    ? "aiMediaSuggestions" : "submissions",
                    used, reserved, 1, monthlyLimit, periodEnd);
        }
        if (enforcementEnabled && dailyExceeded) {
            throw new SubscriptionLimitExceededException(
                    "dailyAiCaptions", dailyUsed, dailyReserved, 1, dailyLimit, dailyReset);
        }

        SubscriptionActionReservation reservation = existing.orElseGet(SubscriptionActionReservation::new);
        reservation.setOrganizationId(organizationId);
        reservation.setInstitutionId(institutionId);
        reservation.setUserId(userId);
        reservation.setEventType(eventType);
        reservation.setQuantity(1);
        reservation.setIdempotencyKey(idempotencyKey);
        reservation.setSourceType(sourceType);
        reservation.setSourceId(sourceId);
        reservation.setStatus(SubscriptionActionReservationStatus.RESERVED);
        reservation.setReservedAt(now);
        reservation.setExpiresAt(now.plus(reservationTtl));
        reservation.setReleasedAt(null);
        reservation = reservationRepository.save(reservation);

        long monthlyRemaining = monthlyLimit - used - reserved - 1;
        long dailyRemaining = dailyLimit == Long.MAX_VALUE
                ? Long.MAX_VALUE : dailyLimit - dailyUsed - dailyReserved - 1;
        boolean warning = monthlyRemaining <= Math.max(1, monthlyLimit / 5)
                || dailyRemaining <= Math.max(1, dailyLimit / 5);
        Instant resetAt = dailyRemaining < monthlyRemaining ? dailyReset : periodEnd;
        return result(reservation, Math.min(monthlyRemaining, dailyRemaining), resetAt, warning);
    }

    private static void assertSameScope(
            SubscriptionActionReservation reservation,
            UUID organizationId,
            UUID institutionId,
            UUID userId,
            SubscriptionUsageEventType eventType,
            UUID sourceId) {
        if (!reservation.getOrganizationId().equals(organizationId)
                || !java.util.Objects.equals(reservation.getInstitutionId(), institutionId)
                || !java.util.Objects.equals(reservation.getUserId(), userId)
                || reservation.getEventType() != eventType
                || !java.util.Objects.equals(reservation.getSourceId(), sourceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key scope mismatch.");
        }
    }

    private OrganizationSubscription lockSubscription(UUID organizationId) {
        OrganizationSubscription subscription = subscriptionRepository.findByOrganizationIdForUpdate(organizationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No subscription is configured for this organization."));
        assertLifecycleAllowsNewUsage(subscription);
        return subscription;
    }

    private void assertLifecycleAllowsNewUsage(OrganizationSubscription subscription) {
        if (enforcementEnabled
                && (subscription.getStatus() == SubscriptionLifecycleStatus.RESTRICTED
                    || subscription.getStatus() == SubscriptionLifecycleStatus.CANCELLED)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The organization subscription is restricted. Contact the Admin Owner.");
        }
    }

    private static long monthlyLimit(SubscriptionPlan plan, SubscriptionUsageEventType eventType) {
        return switch (eventType) {
            case SUBMISSION -> plan.getMonthlySubmissionLimit();
            case AI_CAPTION -> plan.getMonthlyAiCaptionLimit();
            case AI_MEDIA_SUGGESTION -> plan.getMonthlyAiMediaSuggestionLimit();
            default -> throw new IllegalArgumentException("Unsupported subscription action type: " + eventType);
        };
    }

    private static void assertCapacity(
            String resource, long used, long requested, long limit, Instant resetsAt) {
        if (used + requested > limit) {
            throw new SubscriptionLimitExceededException(
                    resource, used, 0, requested, limit, resetsAt);
        }
    }

    private static String normalizedActionKey(
            SubscriptionUsageEventType eventType, String requestKey, UUID sourceId) {
        String prefix = switch (eventType) {
            case AI_CAPTION -> "ai-caption";
            case AI_MEDIA_SUGGESTION -> "ai-media-suggestion";
            case SUBMISSION -> "submission";
            default -> throw new IllegalArgumentException("Unsupported subscription action type: " + eventType);
        };
        if (eventType == SubscriptionUsageEventType.SUBMISSION) {
            return prefix + ":" + sourceId;
        }
        UUID key = requestKey == null || requestKey.isBlank()
                ? UUID.randomUUID()
                : UUID.nameUUIDFromBytes(
                        (sourceId + ":" + requestKey.trim()).getBytes(StandardCharsets.UTF_8));
        return prefix + ":" + key;
    }

    private static ActionReservation result(
            SubscriptionActionReservation reservation,
            long remaining,
            Instant resetAt,
            boolean warning) {
        return new ActionReservation(
                reservation.getId(), Math.max(remaining, 0), resetAt, warning,
                reservation.getStatus() == SubscriptionActionReservationStatus.CONSUMED);
    }

    public record ActionReservation(
            UUID reservationId,
            long remaining,
            Instant resetAt,
            boolean warningThresholdReached,
            boolean alreadyConsumed) {
    }
}
