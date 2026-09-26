package com.dasigconnect.backend.model.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.dasigconnect.backend.model.entity.FacebookEngagementSnapshot;
import com.dasigconnect.backend.model.entity.FacebookHistoricalPost;
import com.dasigconnect.backend.model.entity.FacebookImportJob;
import com.dasigconnect.backend.model.entity.FacebookImportMode;
import com.dasigconnect.backend.model.entity.FacebookImportStatus;
import com.dasigconnect.backend.model.entity.FacebookPostMedia;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FacebookHistoryMapperTest {

    private FacebookHistoryMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FacebookHistoryMapper(new ObjectMapper());
    }

    @Test
    void mapsImportJobWithoutExposingEncryptedPageToken() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        FacebookImportJob job = new FacebookImportJob();
        job.setId(UUID.randomUUID());
        job.setPageId("page-1");
        job.setPageName("Demo Page");
        job.setMode(FacebookImportMode.BACKFILL);
        job.setStatus(FacebookImportStatus.RUNNING);
        job.setStartedBy(user);
        job.setGraphApiVersion("v24.0");

        var dto = mapper.toDto(job);

        assertThat(dto.pageId()).isEqualTo("page-1");
        assertThat(dto.startedByUserId()).isEqualTo(userId);
        assertThat(dto.status()).isEqualTo(FacebookImportStatus.RUNNING);
    }

    @Test
    void mapsStructuredHistoricalAndEngagementMetadata() {
        UUID institutionId = UUID.randomUUID();
        Institution institution = new Institution();
        institution.setId(institutionId);
        FacebookHistoricalPost post = new FacebookHistoricalPost();
        post.setId(UUID.randomUUID());
        post.setPageId("page-1");
        post.setGraphPostId("page-1_42");
        post.setInstitution(institution);
        post.setSourceMetadata("{\"origin\":\"feed\"}");

        FacebookEngagementSnapshot snapshot = new FacebookEngagementSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setHistoricalPost(post);
        snapshot.setPageId("page-1");
        snapshot.setFetchedAt(Instant.parse("2026-09-25T00:00:00Z"));
        snapshot.setReactionBreakdown("{\"like\":12}");
        snapshot.setMetricAvailability("{\"reach\":true}");

        assertThat(mapper.toDto(post).institutionId()).isEqualTo(institutionId);
        assertThat(mapper.toDto(post).sourceMetadata()).containsEntry("origin", "feed");
        assertThat(mapper.toDto(snapshot).reactionBreakdown()).containsEntry("like", 12);
        assertThat(mapper.toDto(snapshot).metricAvailability()).containsEntry("reach", true);
    }

    @Test
    void mapsMediaLinkWithStablePageAndAssetIdentity() {
        FacebookHistoricalPost post = new FacebookHistoricalPost();
        post.setId(UUID.randomUUID());
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        FacebookPostMedia media = new FacebookPostMedia();
        media.setId(UUID.randomUUID());
        media.setHistoricalPost(post);
        media.setPageId("page-1");
        media.setGraphMediaId("media-1");
        media.setMediaAsset(asset);
        media.setPosition(0);
        media.setMediaType("PHOTO");

        var dto = mapper.toDto(media);

        assertThat(dto.historicalPostId()).isEqualTo(post.getId());
        assertThat(dto.mediaAssetId()).isEqualTo(asset.getId());
        assertThat(dto.pageId()).isEqualTo("page-1");
    }
}
