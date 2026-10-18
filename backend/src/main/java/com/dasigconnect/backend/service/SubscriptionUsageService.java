package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.subscription.InstitutionUsageDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionReconciliationDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageEventDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageEventPageDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageMetricDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageSummaryDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import com.dasigconnect.backend.model.entity.SubscriptionUsageUnit;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.SubscriptionUsageEventRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionUsageService {

    private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Manila");

    private final SubscriptionUsageEventRepository usageRepository;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final InstitutionRepository institutionRepository;
    private final SubmissionRepository submissionRepository;
    private final boolean enforcementEnabled;

    public SubscriptionUsageService(
            SubscriptionUsageEventRepository usageRepository,
            OrganizationSubscriptionRepository subscriptionRepository,
            InstitutionRepository institutionRepository,
            SubmissionRepository submissionRepository,
            @Value("${app.subscription.enforcement-enabled:false}") boolean enforcementEnabled) {
        this.usageRepository = usageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.institutionRepository = institutionRepository;
        this.submissionRepository = submissionRepository;
        this.enforcementEnabled = enforcementEnabled;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordForSubmission(
            UUID submissionId,
            UUID userId,
            SubscriptionUsageEventType eventType,
            long quantity,
            SubscriptionUsageUnit unit,
            String idempotencyKey,
            String sourceType,
            Instant occurredAt) {
        com.dasigconnect.backend.model.entity.Submission submission =
                submissionRepository.findByIdWithInstitution(submissionId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Submission not found."));
        Institution institution = submission.getInstitution();
        return record(
                institution.getOrganizationId(), institution.getId(), userId,
                eventType, quantity, unit, idempotencyKey, sourceType,
                submissionId, occurredAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordForInstitution(
            UUID institutionId,
            UUID userId,
            SubscriptionUsageEventType eventType,
            long quantity,
            SubscriptionUsageUnit unit,
            String idempotencyKey,
            String sourceType,
            UUID sourceId,
            Instant occurredAt) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found."));
        return record(
                institution.getOrganizationId(), institutionId, userId, eventType, quantity, unit,
                idempotencyKey, sourceType, sourceId, occurredAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordForOrganization(
            UUID organizationId,
            UUID institutionId,
            UUID userId,
            SubscriptionUsageEventType eventType,
            long quantity,
            SubscriptionUsageUnit unit,
            String idempotencyKey,
            String sourceType,
            UUID sourceId,
            Instant occurredAt) {
        return record(
                organizationId, institutionId, userId, eventType, quantity, unit,
                idempotencyKey, sourceType, sourceId, occurredAt);
    }

    private boolean record(
            UUID organizationId,
            UUID institutionId,
            UUID userId,
            SubscriptionUsageEventType eventType,
            long quantity,
            SubscriptionUsageUnit unit,
            String idempotencyKey,
            String sourceType,
            UUID sourceId,
            Instant occurredAt) {
        if (organizationId == null || eventType == null || unit == null) {
            throw new IllegalArgumentException("Organization, event type, and unit are required.");
        }
        String normalizedKey = requireText(idempotencyKey, "Idempotency key");
        if (normalizedKey.length() > 255) {
            throw new IllegalArgumentException("Idempotency key cannot exceed 255 characters.");
        }
        String normalizedSource = sourceType == null || sourceType.isBlank() ? null : sourceType.trim();
        if (normalizedSource != null && normalizedSource.length() > 60) {
            throw new IllegalArgumentException("Source type cannot exceed 60 characters.");
        }

        return usageRepository.insertIfAbsent(
                UUID.randomUUID(),
                organizationId,
                institutionId,
                userId,
                eventType.name(),
                quantity,
                unit.name(),
                normalizedKey,
                normalizedSource,
                sourceId,
                occurredAt == null ? Instant.now() : occurredAt) == 1;
    }

    @Transactional(readOnly = true)
    public SubscriptionUsageSummaryDto monthlySummary(YearMonth month) {
        OrganizationSubscription subscription = currentSubscription();
        Period period = period(month);
        UUID organizationId = subscription.getOrganization().getId();
        Map<SubscriptionUsageEventType, Long> totals = monthlyTotals(
                organizationId, period.start(), period.end());
        SubscriptionPlan plan = subscription.getPlan();

        List<SubscriptionUsageMetricDto> metrics = List.of(
                new SubscriptionUsageMetricDto(
                        "institutions",
                        usageRepository.currentInstitutionCount(organizationId),
                        plan.getInstitutionLimit(),
                        SubscriptionUsageUnit.COUNT.name()),
                new SubscriptionUsageMetricDto(
                        "activeUsers",
                        usageRepository.currentActiveUserCount(organizationId),
                        plan.getActiveUserLimit(),
                        SubscriptionUsageUnit.COUNT.name()),
                new SubscriptionUsageMetricDto(
                        "mediaStorage",
                        usageRepository.currentMediaStorageBytes(organizationId),
                        plan.getMediaStorageBytes(),
                        SubscriptionUsageUnit.BYTES.name()),
                new SubscriptionUsageMetricDto(
                        "submissions",
                        totals.getOrDefault(SubscriptionUsageEventType.SUBMISSION, 0L),
                        plan.getMonthlySubmissionLimit(),
                        SubscriptionUsageUnit.COUNT.name()),
                new SubscriptionUsageMetricDto(
                        "aiCaptions",
                        totals.getOrDefault(SubscriptionUsageEventType.AI_CAPTION, 0L),
                        plan.getMonthlyAiCaptionLimit(),
                        SubscriptionUsageUnit.COUNT.name()),
                new SubscriptionUsageMetricDto(
                        "aiMediaSuggestions",
                        totals.getOrDefault(SubscriptionUsageEventType.AI_MEDIA_SUGGESTION, 0L),
                        plan.getMonthlyAiMediaSuggestionLimit(),
                        SubscriptionUsageUnit.COUNT.name()));

        return new SubscriptionUsageSummaryDto(
                organizationId,
                month,
                period.start(),
                period.end(),
                subscription.getStatus(),
                enforcementEnabled,
                metrics);
    }

    @Transactional(readOnly = true)
    public List<InstitutionUsageDto> institutionUsage(YearMonth month) {
        OrganizationSubscription subscription = currentSubscription();
        Period period = period(month);
        return usageRepository.institutionUsage(
                        subscription.getOrganization().getId(), period.start(), period.end())
                .stream()
                .map(row -> new InstitutionUsageDto(
                        (UUID) row[0],
                        (String) row[1],
                        number(row[2]),
                        number(row[3]),
                        number(row[4]),
                        number(row[5]),
                        number(row[6])))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionReconciliationDto reconciliation() {
        UUID organizationId = currentSubscription().getOrganization().getId();
        long liveBytes = usageRepository.currentMediaStorageBytes(organizationId);
        long ledgerBytes = usageRepository.ledgerMediaStorageBytes(organizationId);
        return new SubscriptionReconciliationDto(
                organizationId,
                Instant.now(),
                usageRepository.currentInstitutionCount(organizationId),
                usageRepository.currentActiveUserCount(organizationId),
                liveBytes,
                ledgerBytes,
                liveBytes - ledgerBytes);
    }

    @Transactional(readOnly = true)
    public SubscriptionUsageEventPageDto events(int page, int pageSize) {
        UUID organizationId = currentSubscription().getOrganization().getId();
        int safePage = Math.max(page, 0);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<com.dasigconnect.backend.model.entity.SubscriptionUsageEvent> result =
                usageRepository.findByOrganizationIdOrderByOccurredAtDesc(
                        organizationId, PageRequest.of(safePage, safePageSize));
        List<SubscriptionUsageEventDto> items = result.getContent().stream()
                .map(SubscriptionUsageEventDto::from)
                .toList();
        return new SubscriptionUsageEventPageDto(
                items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private OrganizationSubscription currentSubscription() {
        return subscriptionRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No organization subscription is configured."));
    }

    private Map<SubscriptionUsageEventType, Long> monthlyTotals(
            UUID organizationId, Instant start, Instant end) {
        Map<SubscriptionUsageEventType, Long> totals = new EnumMap<>(SubscriptionUsageEventType.class);
        for (Object[] row : usageRepository.aggregateMonthlyConsumption(organizationId, start, end)) {
            totals.put(SubscriptionUsageEventType.valueOf((String) row[0]), number(row[1]));
        }
        return totals;
    }

    private static Period period(YearMonth month) {
        Instant start = month.atDay(1).atStartOfDay(BILLING_ZONE).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(BILLING_ZONE).toInstant();
        return new Period(start, end);
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private record Period(Instant start, Instant end) {
    }
}
