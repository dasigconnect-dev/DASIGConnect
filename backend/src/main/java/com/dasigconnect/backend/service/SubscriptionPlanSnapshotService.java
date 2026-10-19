package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionPlanSnapshotService {
    private final ObjectMapper objectMapper;

    public SubscriptionPlanSnapshotService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String entitlements(SubscriptionPlan plan) {
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
}
