package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubscriptionPlanRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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

    public SubscriptionAdministrationService(
            OrganizationSubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            UserRepository userRepository,
            SubscriptionQueryService queryService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.queryService = queryService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
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
                    HttpStatus.FORBIDDEN, "Only the Admin Owner can change the subscription plan.");
        }
    }
}
