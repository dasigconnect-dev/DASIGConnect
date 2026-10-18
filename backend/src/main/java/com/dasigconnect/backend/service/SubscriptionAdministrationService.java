package com.dasigconnect.backend.service;

import com.dasigconnect.backend.event.SubscriptionLifecycleChangedEvent;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionLifecycleStatus;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubscriptionPlanRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionAdministrationService {

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;
    private final SubscriptionQueryService queryService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionAdministrationService(
            OrganizationSubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            UserRepository userRepository,
            SubscriptionQueryService queryService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.queryService = queryService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SubscriptionStatusDto changePlan(UUID planId, JwtUserDetails actor) {
        requireOwner(actor);
        SubscriptionPlan plan = planRepository.findByIdAndActiveTrue(planId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "The selected subscription plan is unavailable."));
        OrganizationSubscription current = subscriptionRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No organization subscription is configured."));
        OrganizationSubscription subscription = subscriptionRepository
                .findByOrganizationIdForUpdate(current.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "The organization subscription could not be locked."));

        String previousPlan = subscription.getPlanCodeSnapshot();
        if (subscription.getPlan().getId().equals(plan.getId())) {
            return queryService.currentSubscription();
        }

        subscription.setPlan(plan);
        subscription.setPlanCodeSnapshot(plan.getCode());
        subscription.setPlanVersionSnapshot(plan.getVersion());
        subscription.setEntitlementsSnapshot(entitlementsSnapshot(plan));
        subscriptionRepository.saveAndFlush(subscription);

        User actorUser = userRepository.findById(actor.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
        auditLogService.record(
                actorUser,
                "SUBSCRIPTION_PLAN_CHANGED",
                null,
                null,
                subscription.getId(),
                Map.of(
                        "organizationId", subscription.getOrganization().getId().toString(),
                        "previousPlan", previousPlan,
                        "newPlan", plan.getCode(),
                        "planVersion", Integer.toString(plan.getVersion())));
        return queryService.currentSubscription();
    }

    @Transactional
    public SubscriptionStatusDto recordManualRenewal(
            int billingMonths,
            String paymentReference,
            JwtUserDetails actor) {
        requireOwner(actor);
        OrganizationSubscription subscription = lockedSubscription();
        User actorUser = actorUser(actor);
        SubscriptionLifecycleStatus previousStatus = subscription.getStatus();
        Instant now = Instant.now();
        Instant extensionStart = subscription.getCurrentPeriodEnd().isAfter(now)
                ? subscription.getCurrentPeriodEnd() : now;
        Instant renewedUntil = extensionStart.atZone(ZoneId.of("Asia/Manila"))
                .plusMonths(billingMonths)
                .toInstant();
        if (!subscription.getCurrentPeriodEnd().isAfter(now)) {
            subscription.setCurrentPeriodStart(now);
        }
        subscription.setCurrentPeriodEnd(renewedUntil);
        subscription.setGracePeriodEndsAt(null);
        subscription.setStatus(SubscriptionLifecycleStatus.ACTIVE);
        subscriptionRepository.saveAndFlush(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("organizationId", subscription.getOrganization().getId().toString());
        metadata.put("previousStatus", previousStatus.name());
        metadata.put("billingMonths", billingMonths);
        metadata.put("renewedUntil", renewedUntil.toString());
        if (paymentReference != null && !paymentReference.isBlank()) {
            metadata.put("paymentReference", paymentReference.trim());
        }
        auditLogService.record(
                actorUser, "SUBSCRIPTION_MANUAL_RENEWAL_RECORDED",
                null, null, subscription.getId(), metadata);
        if (previousStatus != SubscriptionLifecycleStatus.ACTIVE) {
            eventPublisher.publishEvent(new SubscriptionLifecycleChangedEvent(
                    subscription.getId(), subscription.getOrganization().getName(),
                    previousStatus, SubscriptionLifecycleStatus.ACTIVE,
                    subscription.getCurrentPeriodEnd(), null));
        }
        return queryService.currentSubscription();
    }

    @Transactional
    public SubscriptionStatusDto extendGracePeriod(int days, JwtUserDetails actor) {
        requireOwner(actor);
        OrganizationSubscription subscription = lockedSubscription();
        SubscriptionLifecycleStatus previousStatus = subscription.getStatus();
        if (previousStatus != SubscriptionLifecycleStatus.PAST_DUE
                && previousStatus != SubscriptionLifecycleStatus.GRACE_PERIOD
                && previousStatus != SubscriptionLifecycleStatus.RESTRICTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Grace can only be extended for an overdue subscription.");
        }
        Instant now = Instant.now();
        Instant base = subscription.getGracePeriodEndsAt() != null
                && subscription.getGracePeriodEndsAt().isAfter(now)
                        ? subscription.getGracePeriodEndsAt() : now;
        subscription.setGracePeriodEndsAt(base.plusSeconds(days * 86_400L));
        subscription.setStatus(SubscriptionLifecycleStatus.GRACE_PERIOD);
        subscriptionRepository.saveAndFlush(subscription);

        auditLogService.record(
                actorUser(actor), "SUBSCRIPTION_GRACE_PERIOD_EXTENDED",
                null, null, subscription.getId(),
                Map.of(
                        "organizationId", subscription.getOrganization().getId().toString(),
                        "previousStatus", previousStatus.name(),
                        "days", days,
                        "gracePeriodEndsAt", subscription.getGracePeriodEndsAt().toString()));
        eventPublisher.publishEvent(new SubscriptionLifecycleChangedEvent(
                subscription.getId(), subscription.getOrganization().getName(),
                previousStatus, SubscriptionLifecycleStatus.GRACE_PERIOD,
                subscription.getCurrentPeriodEnd(), subscription.getGracePeriodEndsAt()));
        return queryService.currentSubscription();
    }

    private OrganizationSubscription lockedSubscription() {
        OrganizationSubscription current = subscriptionRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No organization subscription is configured."));
        return subscriptionRepository.findByOrganizationIdForUpdate(current.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "The organization subscription could not be locked."));
    }

    private User actorUser(JwtUserDetails actor) {
        return userRepository.findById(actor.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }

    private String entitlementsSnapshot(SubscriptionPlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("institutionLimit", plan.getInstitutionLimit());
        values.put("activeUserLimit", plan.getActiveUserLimit());
        values.put("mediaStorageBytes", plan.getMediaStorageBytes());
        values.put("monthlySubmissionLimit", plan.getMonthlySubmissionLimit());
        values.put("monthlyAiCaptionLimit", plan.getMonthlyAiCaptionLimit());
        values.put("monthlyAiMediaSuggestionLimit", plan.getMonthlyAiMediaSuggestionLimit());
        values.put("perUserDailyCaptionLimit", plan.getPerUserDailyCaptionLimit());
        values.put("analyticsRetentionDays", plan.getAnalyticsRetentionDays());
        values.put("exportLevel", plan.getExportLevel().name());
        values.put("supportLevel", plan.getSupportLevel().name());
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not snapshot subscription entitlements.", error);
        }
    }

    private static void requireOwner(JwtUserDetails actor) {
        if (actor == null || !"admin".equalsIgnoreCase(actor.role()) || !actor.adminOwner()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only the Admin Owner can manage the subscription.");
        }
    }
}
