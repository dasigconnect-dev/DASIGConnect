package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.model.entity.Organization;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionExportLevel;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.SubscriptionSupportLevel;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubscriptionPlanRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SubscriptionAdministrationServiceTest {

    @Mock private OrganizationSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionQueryService queryService;
    @Mock private AuditLogService auditLogService;
    @Mock private SubscriptionPlanSnapshotService snapshotService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private SubscriptionAdministrationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionAdministrationService(
                subscriptionRepository,
                planRepository,
                userRepository,
                queryService,
                auditLogService,
                snapshotService,
                eventPublisher);
    }

    @Test
    void changePlan_asOwnerUpdatesSnapshotWithoutResettingPeriod() {
        SubscriptionPlan currentPlan = plan("FREE_PILOT", 1);
        SubscriptionPlan targetPlan = plan("PLUS_NETWORK", 2);
        OrganizationSubscription subscription = subscription(currentPlan);
        User ownerUser = new User();
        ownerUser.setId(ownerId);
        SubscriptionStatusDto expected = org.mockito.Mockito.mock(SubscriptionStatusDto.class);
        when(planRepository.findByIdAndActiveTrue(targetPlan.getId()))
                .thenReturn(Optional.of(targetPlan));
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findByOrganizationIdForUpdate(organizationId))
                .thenReturn(Optional.of(subscription));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
        when(queryService.currentSubscription()).thenReturn(expected);
        when(snapshotService.entitlements(targetPlan)).thenReturn("{\"monthlyAiCaptionLimit\":1500,\"analyticsRetentionDays\":null}");

        SubscriptionStatusDto result = service.changePlan(targetPlan.getId(), owner());

        assertThat(result).isSameAs(expected);
        assertThat(subscription.getPlan()).isSameAs(targetPlan);
        assertThat(subscription.getPlanCodeSnapshot()).isEqualTo("PLUS_NETWORK");
        assertThat(subscription.getPlanVersionSnapshot()).isEqualTo(2);
        assertThat(subscription.getEntitlementsSnapshot())
                .contains("\"monthlyAiCaptionLimit\":1500")
                .contains("\"analyticsRetentionDays\":null");
        verify(subscriptionRepository).saveAndFlush(subscription);
        verify(auditLogService).record(
                eq(ownerUser), eq("SUBSCRIPTION_PLAN_CHANGED"), eq(null), eq(null),
                eq(subscription.getId()), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void changePlan_toPaidPlanRequiresVerifiedPayment() {
        SubscriptionPlan paidPlan = plan("PREMIUM_NETWORK", 1);
        paidPlan.setMonthlyPriceCentavos(200);
        when(planRepository.findByIdAndActiveTrue(paidPlan.getId())).thenReturn(Optional.of(paidPlan));

        assertThatThrownBy(() -> service.changePlan(paidPlan.getId(), owner()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT")
                .hasMessageContaining("PayMongo confirms payment");
        verify(subscriptionRepository, never()).findFirstByOrderByCreatedAtAsc();
    }

    @Test
    void changePlan_asPeerAdminIsForbiddenBeforeDatabaseAccess() {
        JwtUserDetails peerAdmin = new JwtUserDetails(
                UUID.randomUUID(), "admin@dasig.test", "admin", null, false);

        assertThatThrownBy(() -> service.changePlan(UUID.randomUUID(), peerAdmin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verify(planRepository, never()).findByIdAndActiveTrue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changePlan_whenAlreadySelectedDoesNotWriteOrAudit() {
        SubscriptionPlan currentPlan = plan("FREE_PILOT", 1);
        OrganizationSubscription subscription = subscription(currentPlan);
        SubscriptionStatusDto expected = org.mockito.Mockito.mock(SubscriptionStatusDto.class);
        when(planRepository.findByIdAndActiveTrue(currentPlan.getId()))
                .thenReturn(Optional.of(currentPlan));
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findByOrganizationIdForUpdate(organizationId))
                .thenReturn(Optional.of(subscription));
        when(queryService.currentSubscription()).thenReturn(expected);

        assertThat(service.changePlan(currentPlan.getId(), owner())).isSameAs(expected);
        verify(subscriptionRepository, never()).saveAndFlush(subscription);
        verify(auditLogService, never()).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void recordManualRenewal_restoresRestrictedSubscriptionAndPublishesEvent() {
        OrganizationSubscription subscription = subscription(plan("PLUS_NETWORK", 1));
        subscription.setStatus(SubscriptionLifecycleStatus.RESTRICTED);
        subscription.setCurrentPeriodEnd(java.time.Instant.now().minusSeconds(3_600));
        User ownerUser = new User();
        ownerUser.setId(ownerId);
        SubscriptionStatusDto expected = org.mockito.Mockito.mock(SubscriptionStatusDto.class);
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findByOrganizationIdForUpdate(organizationId)).thenReturn(Optional.of(subscription));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
        when(queryService.currentSubscription()).thenReturn(expected);

        assertThat(service.recordManualRenewal(1, "OFFICIAL-RECEIPT-1", owner())).isSameAs(expected);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionLifecycleStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isAfter(java.time.Instant.now().plusSeconds(27 * 86_400L));
        assertThat(subscription.getGracePeriodEndsAt()).isNull();
        verify(auditLogService).record(
                eq(ownerUser), eq("SUBSCRIPTION_MANUAL_RENEWAL_RECORDED"), eq(null), eq(null),
                eq(subscription.getId()), org.mockito.ArgumentMatchers.anyMap());
        verify(eventPublisher).publishEvent(
                org.mockito.ArgumentMatchers.any(com.dasigconnect.backend.event.SubscriptionLifecycleChangedEvent.class));
    }

    @Test
    void extendGracePeriod_fromRestrictedRestoresGraceState() {
        OrganizationSubscription subscription = subscription(plan("PLUS_NETWORK", 1));
        subscription.setStatus(SubscriptionLifecycleStatus.RESTRICTED);
        User ownerUser = new User();
        ownerUser.setId(ownerId);
        SubscriptionStatusDto expected = org.mockito.Mockito.mock(SubscriptionStatusDto.class);
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findByOrganizationIdForUpdate(organizationId)).thenReturn(Optional.of(subscription));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(ownerUser));
        when(queryService.currentSubscription()).thenReturn(expected);

        assertThat(service.extendGracePeriod(7, owner())).isSameAs(expected);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionLifecycleStatus.GRACE_PERIOD);
        assertThat(subscription.getGracePeriodEndsAt()).isAfter(java.time.Instant.now().plusSeconds(6 * 86_400L));
        verify(auditLogService).record(
                eq(ownerUser), eq("SUBSCRIPTION_GRACE_PERIOD_EXTENDED"), eq(null), eq(null),
                eq(subscription.getId()), org.mockito.ArgumentMatchers.anyMap());
    }

    private JwtUserDetails owner() {
        return new JwtUserDetails(ownerId, "owner@dasig.test", "admin", null, true);
    }

    private OrganizationSubscription subscription(SubscriptionPlan plan) {
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setName("DASIG Central Visayas");
        OrganizationSubscription subscription = new OrganizationSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setOrganization(organization);
        subscription.setPlan(plan);
        subscription.setPlanCodeSnapshot(plan.getCode());
        subscription.setPlanVersionSnapshot(plan.getVersion());
        subscription.setEntitlementsSnapshot("{}");
        subscription.setStatus(SubscriptionLifecycleStatus.ACTIVE);
        subscription.setCurrentPeriodStart(java.time.Instant.parse("2026-10-01T00:00:00Z"));
        subscription.setCurrentPeriodEnd(java.time.Instant.parse("2026-11-01T00:00:00Z"));
        return subscription;
    }

    private SubscriptionPlan plan(String code, int version) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCode(code);
        plan.setName(code);
        plan.setVersion(version);
        plan.setInstitutionLimit(15);
        plan.setActiveUserLimit(75);
        plan.setMediaStorageBytes(75_000_000_000L);
        plan.setMonthlySubmissionLimit(500);
        plan.setMonthlyAiCaptionLimit(1500);
        plan.setMonthlyAiMediaSuggestionLimit(5000);
        plan.setPerUserDailyCaptionLimit(20);
        plan.setAnalyticsRetentionDays(null);
        plan.setExportLevel(SubscriptionExportLevel.CSV);
        plan.setSupportLevel(SubscriptionSupportLevel.STANDARD_EMAIL);
        return plan;
    }
}
