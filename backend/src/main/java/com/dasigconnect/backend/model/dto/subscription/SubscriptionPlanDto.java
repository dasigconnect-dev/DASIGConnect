package com.dasigconnect.backend.model.dto.subscription;

import com.dasigconnect.backend.model.entity.SubscriptionExportLevel;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.SubscriptionSupportLevel;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionPlanDto(
        UUID id,
        String code,
        String name,
        int version,
        long monthlyPriceCentavos,
        int institutionLimit,
        int activeUserLimit,
        long mediaStorageBytes,
        int monthlySubmissionLimit,
        int monthlyAiCaptionLimit,
        int monthlyAiMediaSuggestionLimit,
        int perUserDailyCaptionLimit,
        Integer analyticsRetentionDays,
        SubscriptionExportLevel exportLevel,
        SubscriptionSupportLevel supportLevel,
        Instant effectiveFrom,
        Instant effectiveUntil) {

    public static SubscriptionPlanDto from(SubscriptionPlan plan) {
        return new SubscriptionPlanDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getVersion(),
                plan.getMonthlyPriceCentavos(),
                plan.getInstitutionLimit(),
                plan.getActiveUserLimit(),
                plan.getMediaStorageBytes(),
                plan.getMonthlySubmissionLimit(),
                plan.getMonthlyAiCaptionLimit(),
                plan.getMonthlyAiMediaSuggestionLimit(),
                plan.getPerUserDailyCaptionLimit(),
                plan.getAnalyticsRetentionDays(),
                plan.getExportLevel(),
                plan.getSupportLevel(),
                plan.getEffectiveFrom(),
                plan.getEffectiveUntil());
    }
}
