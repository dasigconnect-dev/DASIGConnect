package com.dasigconnect.backend.model.mapper;

import com.dasigconnect.backend.model.dto.facebook.FacebookEngagementSnapshotDto;
import com.dasigconnect.backend.model.dto.facebook.FacebookHistoricalPostDto;
import com.dasigconnect.backend.model.dto.facebook.FacebookImportJobDto;
import com.dasigconnect.backend.model.dto.facebook.FacebookPostMediaDto;
import com.dasigconnect.backend.model.entity.FacebookEngagementSnapshot;
import com.dasigconnect.backend.model.entity.FacebookHistoricalPost;
import com.dasigconnect.backend.model.entity.FacebookImportJob;
import com.dasigconnect.backend.model.entity.FacebookPostMedia;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FacebookHistoryMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final ObjectMapper objectMapper;

    public FacebookHistoryMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FacebookImportJobDto toDto(FacebookImportJob job) {
        return new FacebookImportJobDto(
                job.getId(),
                job.getPageId(),
                job.getPageName(),
                job.getScopeInstitution() == null ? null : job.getScopeInstitution().getId(),
                job.getDateFrom(),
                job.getDateTo(),
                job.getMode(),
                job.getStatus(),
                job.getPostsDiscovered(),
                job.getPostsImported(),
                job.getPostsUpdated(),
                job.getMediaDiscovered(),
                job.getMediaImported(),
                job.getMediaFailed(),
                job.getStartedBy().getId(),
                job.getStartedAt(),
                job.getHeartbeatAt(),
                job.getCompletedAt(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getGraphApiVersion(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }

    public FacebookHistoricalPostDto toDto(FacebookHistoricalPost post) {
        return new FacebookHistoricalPostDto(
                post.getId(),
                post.getPageId(),
                post.getGraphPostId(),
                post.getInstitution() == null ? null : post.getInstitution().getId(),
                post.getMessage(),
                post.getPermalinkUrl(),
                post.getCreatedTime(),
                post.getUpdatedTime(),
                post.getPostType(),
                post.getMediaType(),
                post.getAttachmentCount(),
                post.isPublished(),
                post.isDeletedAtSource(),
                readMap(post.getSourceMetadata()),
                post.getFirstImportedAt(),
                post.getLastSyncedAt());
    }

    public FacebookPostMediaDto toDto(FacebookPostMedia media) {
        return new FacebookPostMediaDto(
                media.getId(),
                media.getHistoricalPost().getId(),
                media.getPageId(),
                media.getGraphMediaId(),
                media.getMediaAsset() == null ? null : media.getMediaAsset().getId(),
                media.getPosition(),
                media.getMediaType(),
                media.getWidth(),
                media.getHeight(),
                media.getSourceAltText(),
                media.getSourceUrlExpiresAt());
    }

    public FacebookEngagementSnapshotDto toDto(FacebookEngagementSnapshot snapshot) {
        return new FacebookEngagementSnapshotDto(
                snapshot.getId(),
                snapshot.getHistoricalPost().getId(),
                snapshot.getPageId(),
                snapshot.getFetchedAt(),
                snapshot.getReactionsTotal(),
                readMap(snapshot.getReactionBreakdown()),
                snapshot.getCommentsCount(),
                snapshot.getSharesCount(),
                snapshot.getReach(),
                snapshot.getImpressions(),
                snapshot.getPostClicks(),
                snapshot.getLinkClicks(),
                snapshot.getPhotoViews(),
                snapshot.getVideoViews(),
                snapshot.getFollowerCount(),
                readMap(snapshot.getOrganicMetrics()),
                readMap(snapshot.getPaidMetrics()),
                readMap(snapshot.getMetricAvailability()));
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
