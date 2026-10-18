package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageMetricDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionUsageSummaryDto;
import com.dasigconnect.backend.model.entity.Organization;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import com.dasigconnect.backend.model.entity.SubscriptionUsageUnit;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.SubscriptionUsageEventRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionUsageServiceTest {

    @Mock private SubscriptionUsageEventRepository usageRepository;
    @Mock private OrganizationSubscriptionRepository subscriptionRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private SubmissionRepository submissionRepository;

    private SubscriptionUsageService service;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        service = new SubscriptionUsageService(
                usageRepository, subscriptionRepository, institutionRepository, submissionRepository, false);
        organizationId = UUID.randomUUID();
    }

    @Test
    void recordForOrganization_usesAtomicIdempotentInsert() {
        UUID institutionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-18T00:00:00Z");
        when(usageRepository.insertIfAbsent(
                any(), eq(organizationId), eq(institutionId), eq(userId), eq("AI_CAPTION"),
                eq(1L), eq("COUNT"), eq("ai-caption:test"), eq("AI_CAPTION_REQUEST"),
                eq(sourceId), eq(occurredAt)))
                .thenReturn(1, 0);

        assertTrue(service.recordForOrganization(
                organizationId, institutionId, userId, SubscriptionUsageEventType.AI_CAPTION,
                1, SubscriptionUsageUnit.COUNT, "ai-caption:test", "AI_CAPTION_REQUEST",
                sourceId, occurredAt));
        assertFalse(service.recordForOrganization(
                organizationId, institutionId, userId, SubscriptionUsageEventType.AI_CAPTION,
                1, SubscriptionUsageUnit.COUNT, "ai-caption:test", "AI_CAPTION_REQUEST",
                sourceId, occurredAt));
    }

    @Test
    void monthlySummary_combinesLiveResourcesAndMonthlyLedgerTotals() {
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(subscription()));
        when(usageRepository.currentInstitutionCount(organizationId)).thenReturn(4L);
        when(usageRepository.currentActiveUserCount(organizationId)).thenReturn(12L);
        when(usageRepository.currentMediaStorageBytes(organizationId)).thenReturn(2_500L);
        when(usageRepository.aggregateMonthlyConsumption(eq(organizationId), any(), any()))
                .thenReturn(List.of(
                        new Object[] {"SUBMISSION", 18L},
                        new Object[] {"AI_CAPTION", 31L},
                        new Object[] {"AI_MEDIA_SUGGESTION", 9L}));

        SubscriptionUsageSummaryDto result = service.monthlySummary(YearMonth.of(2026, 10));

        assertEquals(YearMonth.of(2026, 10), result.month());
        assertFalse(result.enforcementEnabled());
        assertMetric(result, "institutions", 4, 10);
        assertMetric(result, "activeUsers", 12, 100);
        assertMetric(result, "mediaStorage", 2_500, 20_000);
        assertMetric(result, "submissions", 18, 500);
        assertMetric(result, "aiCaptions", 31, 1_000);
        assertMetric(result, "aiMediaSuggestions", 9, 750);
        assertEquals(Instant.parse("2026-09-30T16:00:00Z"), result.periodStart());
        assertEquals(Instant.parse("2026-10-31T16:00:00Z"), result.periodEnd());
    }

    @Test
    void reconciliation_reportsDifferenceBetweenLiveAndLedgerStorage() {
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(subscription()));
        when(usageRepository.currentInstitutionCount(organizationId)).thenReturn(3L);
        when(usageRepository.currentActiveUserCount(organizationId)).thenReturn(10L);
        when(usageRepository.currentMediaStorageBytes(organizationId)).thenReturn(4_000L);
        when(usageRepository.ledgerMediaStorageBytes(organizationId)).thenReturn(3_750L);

        var result = service.reconciliation();

        assertEquals(250L, result.mediaStorageDriftBytes());
        assertEquals(4_000L, result.liveMediaStorageBytes());
        assertEquals(3_750L, result.ledgerMediaStorageBytes());
        verify(usageRepository).currentInstitutionCount(organizationId);
    }

    private OrganizationSubscription subscription() {
        Organization organization = new Organization();
        organization.setId(organizationId);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setInstitutionLimit(10);
        plan.setActiveUserLimit(100);
        plan.setMediaStorageBytes(20_000);
        plan.setMonthlySubmissionLimit(500);
        plan.setMonthlyAiCaptionLimit(1_000);
        plan.setMonthlyAiMediaSuggestionLimit(750);

        OrganizationSubscription subscription = new OrganizationSubscription();
        subscription.setOrganization(organization);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionLifecycleStatus.ACTIVE);
        return subscription;
    }

    private static void assertMetric(
            SubscriptionUsageSummaryDto summary, String resource, long used, long limit) {
        SubscriptionUsageMetricDto metric = summary.metrics().stream()
                .filter(candidate -> resource.equals(candidate.resource()))
                .findFirst()
                .orElseThrow();
        assertEquals(used, metric.used());
        assertEquals(limit, metric.limit());
    }
}
