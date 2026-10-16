package com.dasigconnect.backend.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.ManyToOne;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FacebookHistoryDomainTest {

    @Test
    void importJob_defaultsToQueuedAndInitializesIdentityAndTimestamps() {
        FacebookImportJob job = new FacebookImportJob();

        job.onCreate();

        assertThat(job.getId()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(FacebookImportStatus.QUEUED);
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isNotNull();
    }

    @Test
    void historicalPost_initializesStableImportAndSyncTimestamps() {
        FacebookHistoricalPost post = new FacebookHistoricalPost();
        post.setSourceMetadata(null);

        post.onCreate();

        assertThat(post.getId()).isNotNull();
        assertThat(post.getSourceMetadata()).isEqualTo("{}");
        assertThat(post.getFirstImportedAt()).isNotNull();
        assertThat(post.getLastSyncedAt()).isNotNull();
    }

    @Test
    void engagementSnapshot_normalizesMissingStructuredMetricMaps() {
        FacebookEngagementSnapshot snapshot = new FacebookEngagementSnapshot();
        snapshot.setReactionBreakdown(" ");
        snapshot.setOrganicMetrics(null);
        snapshot.setPaidMetrics("");
        snapshot.setMetricAvailability(null);

        snapshot.onCreate();

        assertThat(snapshot.getId()).isNotNull();
        assertThat(snapshot.getFetchedAt()).isNotNull();
        assertThat(snapshot.getReactionBreakdown()).isEqualTo("{}");
        assertThat(snapshot.getOrganicMetrics()).isEqualTo("{}");
        assertThat(snapshot.getPaidMetrics()).isEqualTo("{}");
        assertThat(snapshot.getMetricAvailability()).isEqualTo("{}");
    }

    @Test
    void ordinaryMediaDefaultsToUserUploadProvenance() {
        MediaAsset asset = new MediaAsset();

        asset.onCreate();

        assertThat(asset.getSourceType()).isEqualTo(MediaAssetSourceType.USER_UPLOAD);
        assertThat(asset.isSystemManaged()).isFalse();
    }

    @Test
    void historicalPostLinksMayReuseOneImportedMediaAsset() {
        var mediaAssetField = Arrays.stream(FacebookPostMedia.class.getDeclaredFields())
                .filter(field -> field.getName().equals("mediaAsset"))
                .findFirst()
                .orElseThrow();

        assertThat(mediaAssetField.isAnnotationPresent(ManyToOne.class)).isTrue();
    }
}
