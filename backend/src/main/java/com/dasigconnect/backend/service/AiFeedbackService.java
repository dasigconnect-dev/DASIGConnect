package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.ai.AiFeedbackMetricsDto;
import com.dasigconnect.backend.model.dto.ai.SearchFeedbackRequestDto;
import com.dasigconnect.backend.model.entity.AiInteractionLog;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-4.6 AI feedback loop. Records user feedback on AI surfaces (currently media search) into
 * {@code ai_interaction_log} and exposes acceptance/thumbs precision metrics. Feedback writes are
 * best-effort — a logging failure must never break the user's action (same discipline as the T1
 * notification block).
 */
@Service
public class AiFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(AiFeedbackService.class);
    private static final String SURFACE_SEARCH = "media_search";

    private final AiInteractionLogRepository aiInteractionLogRepository;
    private final MediaAssetRepository mediaAssetRepository;

    public AiFeedbackService(AiInteractionLogRepository aiInteractionLogRepository,
                             MediaAssetRepository mediaAssetRepository) {
        this.aiInteractionLogRepository = aiInteractionLogRepository;
        this.mediaAssetRepository = mediaAssetRepository;
    }

    @Transactional
    public void recordSearchFeedback(SearchFeedbackRequestDto dto, JwtUserDetails user) {
        try {
            Optional<MediaAsset> assetOpt = mediaAssetRepository.findActiveById(dto.getAssetId());
            if (assetOpt.isEmpty()) {
                return; // asset gone — nothing to attribute feedback to
            }
            MediaAsset asset = assetOpt.get();
            UUID assetInstitutionId = asset.getInstitution().getId();

            // A non-admin can only give feedback on their own institution's assets.
            if (!isAdmin(user) && !assetInstitutionId.equals(user.institutionId())) {
                return;
            }

            AiInteractionLog entry = new AiInteractionLog();
            entry.setInstitutionId(assetInstitutionId);
            entry.setInteractionType(SURFACE_SEARCH);
            entry.setActionTaken(normalizeAction(dto.getAction()));
            entry.setTargetAssetId(asset.getId());
            entry.setResultRank(dto.getRank());
            entry.setRating(ratingFor(dto.getAction()));
            entry.setQueryText(trimToLength(dto.getQuery(), 500));
            aiInteractionLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to record search feedback for asset {}: {}", dto.getAssetId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AiFeedbackMetricsDto getMetrics(JwtUserDetails user) {
        boolean networkScope = isAdmin(user);
        UUID institutionId = networkScope ? null : user.institutionId();

        List<AiFeedbackMetricsDto.Surface> surfaces = new ArrayList<>();
        for (Object[] row : aiInteractionLogRepository.aggregateBySurface(institutionId)) {
            String surface = (String) row[0];
            long total = toLong(row[1]);
            long applied = toLong(row[2]);
            long dismissed = toLong(row[3]);
            long thumbsUp = toLong(row[4]);
            long thumbsDown = toLong(row[5]);
            surfaces.add(new AiFeedbackMetricsDto.Surface(
                    surface, total, applied, dismissed, thumbsUp, thumbsDown,
                    rate(applied, applied + dismissed),
                    rate(thumbsUp, thumbsUp + thumbsDown)));
        }
        return new AiFeedbackMetricsDto(networkScope, surfaces);
    }

    private String normalizeAction(String action) {
        String a = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return a.length() > 30 ? a.substring(0, 30) : a;
    }

    private Short ratingFor(String action) {
        String a = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (a.equals("thumbs_up")) {
            return (short) 1;
        }
        if (a.equals("thumbs_down")) {
            return (short) -1;
        }
        return null;
    }

    private String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    /** Ratio or null when the denominator is zero — never quote a rate from no data (eval §9.1). */
    private Double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return Math.round((double) numerator / denominator * 10000.0) / 10000.0;
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private boolean isAdmin(JwtUserDetails user) {
        return user.role() != null && user.role().toLowerCase(Locale.ROOT).contains("admin");
    }
}
