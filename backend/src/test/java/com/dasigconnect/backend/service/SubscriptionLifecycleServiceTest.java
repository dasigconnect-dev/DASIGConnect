package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.event.SubscriptionLifecycleChangedEvent;
import com.dasigconnect.backend.model.entity.Organization;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleServiceTest {

    @Mock private OrganizationSubscriptionRepository subscriptionRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final Instant now = Instant.parse("2026-10-18T04:00:00Z");
    private OrganizationSubscription subscription;

    @BeforeEach
    void setUp() {
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("DASIG Central Visayas");
        subscription = new OrganizationSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setOrganization(organization);
        subscription.setCurrentPeriodStart(now.minusSeconds(31 * 86_400L));
        subscription.setCurrentPeriodEnd(now.minusSeconds(60));
    }

    @Test
    void evaluate_whenLifecycleDisabled_doesNotReadOrWrite() {
        SubscriptionLifecycleService service = service(false);

        assertThat(service.evaluate(now)).isEmpty();

        verify(subscriptionRepository, never()).findFirstByOrderByCreatedAtAsc();
    }

    @Test
    void evaluate_expiredActiveSubscription_movesToPastDueAndSetsGraceDeadline() {
        subscription.setStatus(SubscriptionLifecycleStatus.ACTIVE);
        stubSubscription();

        var transition = service(true).evaluate(now).orElseThrow();

        assertThat(transition.previousStatus()).isEqualTo(SubscriptionLifecycleStatus.ACTIVE);
        assertThat(transition.currentStatus()).isEqualTo(SubscriptionLifecycleStatus.PAST_DUE);
        assertThat(subscription.getGracePeriodEndsAt())
                .isEqualTo(subscription.getCurrentPeriodEnd().plusSeconds(7 * 86_400L));
        verify(subscriptionRepository).saveAndFlush(subscription);
        verify(auditLogService).recordByActorId(
                eq(null), eq("SUBSCRIPTION_LIFECYCLE_CHANGED"), eq(null), eq(null),
                eq(subscription.getId()), any());
        verify(eventPublisher).publishEvent(any(SubscriptionLifecycleChangedEvent.class));
    }

    @Test
    void evaluate_pastDueAfterNoticeWindow_movesToGrace() {
        subscription.setStatus(SubscriptionLifecycleStatus.PAST_DUE);
        subscription.setCurrentPeriodEnd(now.minusSeconds(2 * 86_400L));
        subscription.setGracePeriodEndsAt(now.plusSeconds(5 * 86_400L));
        stubSubscription();

        assertThat(service(true).evaluate(now).orElseThrow().currentStatus())
                .isEqualTo(SubscriptionLifecycleStatus.GRACE_PERIOD);
    }

    @Test
    void evaluate_expiredGracePeriod_movesToRestricted() {
        subscription.setStatus(SubscriptionLifecycleStatus.GRACE_PERIOD);
        subscription.setGracePeriodEndsAt(now.minusSeconds(1));
        stubSubscription();

        assertThat(service(true).evaluate(now).orElseThrow().currentStatus())
                .isEqualTo(SubscriptionLifecycleStatus.RESTRICTED);
    }

    @Test
    void evaluate_activeSubscriptionBeforePeriodEnd_doesNothing() {
        subscription.setStatus(SubscriptionLifecycleStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(now.plusSeconds(60));
        stubSubscription();

        assertThat(service(true).evaluate(now)).isEmpty();
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    private void stubSubscription() {
        when(subscriptionRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findByOrganizationIdForUpdate(subscription.getOrganization().getId()))
                .thenReturn(Optional.of(subscription));
    }

    private SubscriptionLifecycleService service(boolean enabled) {
        return new SubscriptionLifecycleService(
                subscriptionRepository, auditLogService, eventPublisher, enabled, 1, 7);
    }
}
