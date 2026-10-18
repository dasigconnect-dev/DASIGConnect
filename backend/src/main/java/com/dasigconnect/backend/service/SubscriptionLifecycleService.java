package com.dasigconnect.backend.service;

import com.dasigconnect.backend.event.SubscriptionLifecycleChangedEvent;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionLifecycleService {

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean lifecycleEnabled;
    private final Duration pastDueNoticePeriod;
    private final Duration gracePeriod;

    public SubscriptionLifecycleService(
            OrganizationSubscriptionRepository subscriptionRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.subscription.lifecycle-enabled:false}") boolean lifecycleEnabled,
            @Value("${app.subscription.past-due-notice-days:1}") long pastDueNoticeDays,
            @Value("${app.subscription.grace-period-days:7}") long gracePeriodDays) {
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.lifecycleEnabled = lifecycleEnabled;
        this.pastDueNoticePeriod = Duration.ofDays(Math.max(pastDueNoticeDays, 0));
        this.gracePeriod = Duration.ofDays(Math.max(gracePeriodDays, 1));
    }

    @Transactional
    public Optional<Transition> evaluate(Instant now) {
        if (!lifecycleEnabled) {
            return Optional.empty();
        }
        OrganizationSubscription current = subscriptionRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        if (current == null) {
            return Optional.empty();
        }
        OrganizationSubscription subscription = subscriptionRepository
                .findByOrganizationIdForUpdate(current.getOrganization().getId())
                .orElse(null);
        if (subscription == null) {
            return Optional.empty();
        }

        SubscriptionLifecycleStatus previous = subscription.getStatus();
        SubscriptionLifecycleStatus next = nextStatus(subscription, now);
        if (next == null) {
            return Optional.empty();
        }
        if (next == SubscriptionLifecycleStatus.PAST_DUE && subscription.getGracePeriodEndsAt() == null) {
            subscription.setGracePeriodEndsAt(subscription.getCurrentPeriodEnd().plus(gracePeriod));
        }
        subscription.setStatus(next);
        subscriptionRepository.saveAndFlush(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("organizationId", subscription.getOrganization().getId().toString());
        metadata.put("previousStatus", previous.name());
        metadata.put("newStatus", next.name());
        metadata.put("currentPeriodEnd", subscription.getCurrentPeriodEnd().toString());
        if (subscription.getGracePeriodEndsAt() != null) {
            metadata.put("gracePeriodEndsAt", subscription.getGracePeriodEndsAt().toString());
        }
        auditLogService.recordByActorId(
                null, "SUBSCRIPTION_LIFECYCLE_CHANGED", null, null, subscription.getId(), metadata);
        eventPublisher.publishEvent(event(subscription, previous, next));
        return Optional.of(new Transition(previous, next));
    }

    private SubscriptionLifecycleStatus nextStatus(OrganizationSubscription subscription, Instant now) {
        SubscriptionLifecycleStatus status = subscription.getStatus();
        if ((status == SubscriptionLifecycleStatus.ACTIVE || status == SubscriptionLifecycleStatus.TRIAL)
                && !subscription.getCurrentPeriodEnd().isAfter(now)) {
            return SubscriptionLifecycleStatus.PAST_DUE;
        }
        if (status == SubscriptionLifecycleStatus.PAST_DUE
                && !subscription.getCurrentPeriodEnd().plus(pastDueNoticePeriod).isAfter(now)) {
            return SubscriptionLifecycleStatus.GRACE_PERIOD;
        }
        if (status == SubscriptionLifecycleStatus.GRACE_PERIOD
                && subscription.getGracePeriodEndsAt() != null
                && !subscription.getGracePeriodEndsAt().isAfter(now)) {
            return SubscriptionLifecycleStatus.RESTRICTED;
        }
        return null;
    }

    static SubscriptionLifecycleChangedEvent event(
            OrganizationSubscription subscription,
            SubscriptionLifecycleStatus previous,
            SubscriptionLifecycleStatus current) {
        return new SubscriptionLifecycleChangedEvent(
                subscription.getId(),
                subscription.getOrganization().getName(),
                previous,
                current,
                subscription.getCurrentPeriodEnd(),
                subscription.getGracePeriodEndsAt());
    }

    public record Transition(
            SubscriptionLifecycleStatus previousStatus,
            SubscriptionLifecycleStatus currentStatus) {
    }
}
