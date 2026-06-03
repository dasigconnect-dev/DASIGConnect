package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.ai.AiFeedbackMetricsDto;
import com.dasigconnect.backend.model.dto.ai.SearchFeedbackRequestDto;
import com.dasigconnect.backend.model.entity.AiInteractionLog;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiFeedbackServiceTest {

    @Mock private AiInteractionLogRepository aiInteractionLogRepository;
    @Mock private MediaAssetRepository mediaAssetRepository;

    private AiFeedbackService service;
    private UUID institutionId;

    @BeforeEach
    void setUp() {
        service = new AiFeedbackService(aiInteractionLogRepository, mediaAssetRepository);
        institutionId = UUID.randomUUID();
    }

    @Test
    void recordSearchFeedback_savesLogWithAssetInstitutionAndRating() {
        UUID assetId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));
        JwtUserDetails user = new JwtUserDetails(UUID.randomUUID(), "c@cit.edu.ph", "CONTRIBUTOR", institutionId);

        SearchFeedbackRequestDto dto = new SearchFeedbackRequestDto();
        dto.setAssetId(assetId);
        dto.setAction("thumbs_up");
        dto.setQuery("red velvet dessert");
        dto.setRank(2);

        service.recordSearchFeedback(dto, user);

        ArgumentCaptor<AiInteractionLog> captor = ArgumentCaptor.forClass(AiInteractionLog.class);
        verify(aiInteractionLogRepository).save(captor.capture());
        AiInteractionLog saved = captor.getValue();
        assertEquals("media_search", saved.getInteractionType());
        assertEquals("thumbs_up", saved.getActionTaken());
        assertEquals(assetId, saved.getTargetAssetId());
        assertEquals(institutionId, saved.getInstitutionId());
        assertEquals(2, saved.getResultRank());
        assertEquals((short) 1, saved.getRating());
        assertEquals("red velvet dessert", saved.getQueryText());
        assertNull(saved.getSubmissionId());
    }

    @Test
    void recordSearchFeedback_skipsWhenAssetMissing() {
        UUID assetId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.empty());
        JwtUserDetails user = new JwtUserDetails(UUID.randomUUID(), "c@cit.edu.ph", "CONTRIBUTOR", institutionId);

        SearchFeedbackRequestDto dto = new SearchFeedbackRequestDto();
        dto.setAssetId(assetId);
        dto.setAction("thumbs_down");

        service.recordSearchFeedback(dto, user);

        verify(aiInteractionLogRepository, never()).save(any());
    }

    @Test
    void recordSearchFeedback_blocksCrossTenantForNonAdmin() {
        UUID assetId = UUID.randomUUID();
        UUID otherInstitution = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, otherInstitution)));
        JwtUserDetails user = new JwtUserDetails(UUID.randomUUID(), "c@cit.edu.ph", "CONTRIBUTOR", institutionId);

        SearchFeedbackRequestDto dto = new SearchFeedbackRequestDto();
        dto.setAssetId(assetId);
        dto.setAction("selected");

        service.recordSearchFeedback(dto, user);

        verify(aiInteractionLogRepository, never()).save(any());
    }

    @Test
    void getMetrics_computesAcceptanceAndThumbsRates() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "a@dasig.test", "ADMINISTRATOR", null);
        when(aiInteractionLogRepository.aggregateBySurface(null)).thenReturn(List.<Object[]>of(
                new Object[] {"media_search", 10L, 4L, 2L, 5L, 1L}));

        AiFeedbackMetricsDto metrics = service.getMetrics(admin);

        assertEquals(true, metrics.networkScope());
        assertEquals(1, metrics.surfaces().size());
        AiFeedbackMetricsDto.Surface s = metrics.surfaces().get(0);
        assertEquals("media_search", s.surface());
        assertEquals(10L, s.total());
        assertEquals(0.6667, s.acceptanceRate(), 0.0001);  // 4 / (4+2)
        assertEquals(0.8333, s.thumbsRate(), 0.0001);      // 5 / (5+1)
    }

    @Test
    void getMetrics_nullRateWhenNoData() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "a@dasig.test", "ADMINISTRATOR", null);
        when(aiInteractionLogRepository.aggregateBySurface(null)).thenReturn(List.<Object[]>of(
                new Object[] {"tag_classification", 3L, 0L, 0L, 0L, 0L}));

        AiFeedbackMetricsDto.Surface s = service.getMetrics(admin).surfaces().get(0);
        assertNull(s.acceptanceRate());
        assertNull(s.thumbsRate());
    }

    private static MediaAsset asset(UUID id, UUID institutionId) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setName("CIT-U");
        User uploader = new User();
        uploader.setId(UUID.randomUUID());
        uploader.setEmail("c@cit.edu.ph");
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setInstitution(institution);
        asset.setUploader(uploader);
        asset.setAssetCode("ASSET-" + id.toString().substring(0, 8));
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(1024L);
        return asset;
    }
}
