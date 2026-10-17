package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.model.entity.Organization;
import com.dasigconnect.backend.model.entity.OrganizationStatus;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionExportLevel;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.SubscriptionSupportLevel;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubscriptionPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SubscriptionQueryServiceTest {

    private final OrganizationSubscriptionRepository subscriptionRepository =
            mock(OrganizationSubscriptionRepository.class);
    private final SubscriptionPlanRepository planRepository = mock(SubscriptionPlanRepository.class);

    @Test
    void currentSubscription_mapsSeededOrganizationPlanAndDisabledEnforcement() {
        Organization organization = organization();
        SubscriptionPlan plan = plan("PLUS_NETWORK", "Plus Network", 749900);
        OrganizationSubscription subscription = new OrganizationSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setOrganization(organization);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionLifecycleStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(Instant.parse("2026-11-01T00:00:00Z"));

        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));

        SubscriptionQueryService service = new SubscriptionQueryService(
                subscriptionRepository, planRepository, false);
        SubscriptionStatusDto result = service.currentSubscription();

        assertThat(result.organizationId()).isEqualTo(organization.getId());
        assertThat(result.subscriptionStatus()).isEqualTo(SubscriptionLifecycleStatus.ACTIVE);
        assertThat(result.plan().code()).isEqualTo("PLUS_NETWORK");
        assertThat(result.plan().monthlyPriceCentavos()).isEqualTo(749900);
        assertThat(result.enforcementEnabled()).isFalse();
    }

    @Test
    void currentSubscription_whenMissing_returnsNotFound() {
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        SubscriptionQueryService service = new SubscriptionQueryService(
                subscriptionRepository, planRepository, false);

        assertThatThrownBy(service::currentSubscription)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void activePlans_preservesRepositoryOrderingAndMapsLimits() {
        SubscriptionPlan free = plan("FREE_PILOT", "Free Pilot", 0);
        SubscriptionPlan plus = plan("PLUS_NETWORK", "Plus Network", 749900);
        when(planRepository.findAllByActiveTrueOrderByMonthlyPriceCentavosAscVersionDesc())
                .thenReturn(List.of(free, plus));

        SubscriptionQueryService service = new SubscriptionQueryService(
                subscriptionRepository, planRepository, false);

        assertThat(service.activePlans())
                .extracting(plan -> plan.code())
                .containsExactly("FREE_PILOT", "PLUS_NETWORK");
    }

    private static Organization organization() {
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("DASIG Central Visayas");
        organization.setSlug("dasig-central-visayas");
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organization;
    }

    private static SubscriptionPlan plan(String code, String name, long price) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCode(code);
        plan.setName(name);
        plan.setVersion(1);
        plan.setMonthlyPriceCentavos(price);
        plan.setInstitutionLimit(15);
        plan.setActiveUserLimit(75);
        plan.setMediaStorageBytes(75_000_000_000L);
        plan.setMonthlySubmissionLimit(500);
        plan.setMonthlyAiCaptionLimit(1500);
        plan.setMonthlyAiMediaSuggestionLimit(5000);
        plan.setPerUserDailyCaptionLimit(20);
        plan.setAnalyticsRetentionDays(365);
        plan.setExportLevel(SubscriptionExportLevel.CSV);
        plan.setSupportLevel(SubscriptionSupportLevel.STANDARD_EMAIL);
        plan.setEffectiveFrom(Instant.parse("2026-10-18T00:00:00Z"));
        return plan;
    }
}
