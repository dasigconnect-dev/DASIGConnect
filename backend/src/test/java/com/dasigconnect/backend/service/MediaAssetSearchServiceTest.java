package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.model.dto.media.MediaAssetSearchRequestDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaAssetSearchHit;
import com.dasigconnect.backend.repository.MediaAssetSearchRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaAssetSearchServiceTest {

    @Mock private MediaAssetSearchRepository searchRepository;
    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private MediaAssetEmbeddingRepository embeddingRepository;
    @Mock private VoyageAIClient voyageAIClient;

    private MediaAssetSearchService service;
    private UUID institutionId;
    private JwtUserDetails user;

    @BeforeEach
    void setUp() {
        service = new MediaAssetSearchService(searchRepository, mediaAssetRepository, embeddingRepository, voyageAIClient, new SearchQueryParser());
        institutionId = UUID.randomUUID();
        user = new JwtUserDetails(UUID.randomUUID(), "contributor@cit.edu.ph", "CONTRIBUTOR", institutionId);
    }

    @Test
    void search_contributorUsesOwnInstitutionAndReturnsReasons() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = mediaAsset(assetId, institutionId);
        asset.setTitle("Robotics Prototype Demo");
        asset.setAiDescription("Students presenting a prototype.");
        when(searchRepository.searchLexical("robotics prototype", institutionId, false, null, 72, 0))
                .thenReturn(List.of(new MediaAssetSearchHit(assetId, 0.42, 1)));
        when(searchRepository.countLexical("robotics prototype", institutionId, false, null))
                .thenReturn(1);
        when(mediaAssetRepository.findActiveByIdsWithInstitutionAndUploader(List.of(assetId))).thenReturn(List.of(asset));

        MediaAssetSearchRequestDto dto = new MediaAssetSearchRequestDto();
        dto.setQuery("robotics prototype");

        var response = service.search(dto, user);

        assertEquals(1, response.getTotalCount());
        assertEquals(assetId, response.getItems().get(0).getAsset().getId());
        assertEquals("Title match", response.getItems().get(0).getMatchReasons().get(0));
    }

    @Test
    void search_foreignInstitutionForContributorReturns403() {
        MediaAssetSearchRequestDto dto = new MediaAssetSearchRequestDto();
        dto.setQuery("award");
        dto.setInstitutionId(UUID.randomUUID());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.search(dto, user));

        assertEquals(403, ex.getStatusCode().value());
        verify(searchRepository, never()).searchLexical(any(), any(), anyBoolean(), any(), anyInt(), anyInt());
    }

    @Test
    void search_adminWithoutScopeReturns400() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@dasig.test", "ADMINISTRATOR", null);
        MediaAssetSearchRequestDto dto = new MediaAssetSearchRequestDto();
        dto.setQuery("award");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.search(dto, admin));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void search_adminNetworkAllowsNullInstitution() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@dasig.test", "ADMINISTRATOR", null);
        MediaAssetSearchRequestDto dto = new MediaAssetSearchRequestDto();
        dto.setQuery("award");
        dto.setScope("network");
        dto.setMediaType("image");
        dto.setPageSize(10);

        when(searchRepository.searchLexical("award", null, true, "image", 60, 0)).thenReturn(List.of());

        var response = service.search(dto, admin);

        assertEquals(0, response.getTotalCount());
        verify(mediaAssetRepository, never()).findActiveByIdsWithInstitutionAndUploader(any());
    }

    @Test
    void search_fusesLexicalSemanticAndVisualRanks() {
        UUID lexicalAssetId = UUID.randomUUID();
        UUID semanticAssetId = UUID.randomUUID();
        UUID visualAssetId = UUID.randomUUID();
        MediaAsset lexicalAsset = mediaAsset(lexicalAssetId, institutionId);
        lexicalAsset.setTitle("Delicious cookie booth");
        MediaAsset semanticAsset = mediaAsset(semanticAssetId, institutionId);
        semanticAsset.setTitle("Campus dessert tasting");
        MediaAsset visualAsset = mediaAsset(visualAssetId, institutionId);
        visualAsset.setTitle("Snack table photo");

        when(voyageAIClient.embedQuery("delicious cookie")).thenReturn("[0.1,0.2]");
        when(voyageAIClient.embedMultimodalTextQuery("delicious cookie")).thenReturn("[0.3,0.4]");
        when(searchRepository.searchLexical("delicious cookie", institutionId, false, null, 72, 0))
                .thenReturn(List.of(new MediaAssetSearchHit(lexicalAssetId, 0.51, 1)));
        when(searchRepository.countLexical("delicious cookie", institutionId, false, null)).thenReturn(1);
        when(embeddingRepository.findTopSimilarWithScoreScoped(
                institutionId, false, MediaAssetEmbeddingType.SEMANTIC,
                "[0.1,0.2]", null, 72))
                .thenReturn(List.<Object[]>of(
                        row(semanticAssetId, 0.91),
                        row(lexicalAssetId, 0.87)));
        when(embeddingRepository.findTopSimilarWithScoreScoped(
                institutionId, false, MediaAssetEmbeddingType.IMAGE,
                "[0.3,0.4]", null, 72))
                .thenReturn(List.<Object[]>of(row(visualAssetId, 0.94)));
        when(mediaAssetRepository.findActiveByIdsWithInstitutionAndUploader(any()))
                .thenReturn(List.of(lexicalAsset, semanticAsset, visualAsset));

        MediaAssetSearchRequestDto dto = new MediaAssetSearchRequestDto();
        dto.setQuery("delicious cookie");

        var response = service.search(dto, user);

        assertEquals(3, response.getItems().size());
        assertEquals(lexicalAssetId, response.getItems().get(0).getAsset().getId());
        assertEquals(1, response.getItems().get(0).getLexicalRank());
        assertEquals(2, response.getItems().get(0).getSemanticRank());
        assertEquals("Semantic similarity", response.getItems().stream()
                .filter(item -> item.getAsset().getId().equals(semanticAssetId))
                .findFirst()
                .orElseThrow()
                .getMatchReasons()
                .get(0));
        assertEquals("Visual similarity", response.getItems().stream()
                .filter(item -> item.getAsset().getId().equals(visualAssetId))
                .findFirst()
                .orElseThrow()
                .getMatchReasons()
                .get(0));
    }

    @Test
    void search_returnsLexicalResultsWhenVoyageFails() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = mediaAsset(assetId, institutionId);
        when(voyageAIClient.embedQuery("award")).thenThrow(new VoyageAIClient.VoyageApiException("no key"));
        when(voyageAIClient.embedMultimodalTextQuery("award")).thenThrow(new VoyageAIClient.VoyageApiException("no key"));
        when(searchRepository.searchLexical("award", institutionId, false, null, 72, 0))
                .thenReturn(List.of(new MediaAssetSearchHit(assetId, 0.35, 1)));
        when(searchRepository.countLexical("award", institutionId, false, null)).thenReturn(1);
        when(mediaAssetRepository.findActiveByIdsWithInstitutionAndUploader(List.of(assetId))).thenReturn(List.of(asset));

        MediaAssetSearchRequestDto dto = new MediaAssetSearchRequestDto();
        dto.setQuery("award");

        var response = service.search(dto, user);

        assertEquals(1, response.getItems().size());
        assertEquals(assetId, response.getItems().get(0).getAsset().getId());
        verify(embeddingRepository, never()).findTopSimilarWithScoreScoped(
                any(), anyBoolean(), any(MediaAssetEmbeddingType.class), anyString(), any(), anyInt());
    }

    @Test
    void suggest_blankQuery_returnsEmptyWithoutHittingRepository() {
        var response = service.suggest("   ", null, null, null, user);

        assertEquals(0, response.suggestions().size());
        verify(searchRepository, never()).suggest(anyString(), any(), anyBoolean(), anyInt());
    }

    @Test
    void suggest_contributorScoped_dedupesAcrossTypesAndPreservesOrder() {
        when(searchRepository.suggest(eq("food"), eq(institutionId), eq(false), anyInt()))
                .thenReturn(List.of(
                        new Object[] {"food", "tag", 0},
                        new Object[] {"food", "file", 2},            // duplicate text → dropped
                        new Object[] {"food outreach", "collection", 1}));

        var response = service.suggest("food", null, null, null, user);

        assertEquals(2, response.suggestions().size());
        assertEquals("food", response.suggestions().get(0).text());
        assertEquals("tag", response.suggestions().get(0).type());
        assertEquals("food outreach", response.suggestions().get(1).text());
        verify(searchRepository).suggest(eq("food"), eq(institutionId), eq(false), anyInt());
    }

    @Test
    void suggest_respectsRequestedLimit() {
        when(searchRepository.suggest(eq("a"), eq(institutionId), eq(false), anyInt()))
                .thenReturn(List.of(
                        new Object[] {"alpha", "tag", 1},
                        new Object[] {"beta", "file", 1},
                        new Object[] {"gamma", "uploader", 1},
                        new Object[] {"delta", "folder", 1}));

        var response = service.suggest("a", null, null, 2, user);

        assertEquals(2, response.suggestions().size());
    }

    @Test
    void suggest_adminWithoutInstitutionOrScope_throws() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@dasig.gov", "ADMINISTRATOR", null);

        assertThrows(ResponseStatusException.class, () -> service.suggest("food", null, null, null, admin));
    }

    private static MediaAsset mediaAsset(UUID id, UUID institutionId) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setName("CIT-U");

        User uploader = new User();
        uploader.setId(UUID.randomUUID());
        uploader.setEmail("contributor@cit.edu.ph");

        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setInstitution(institution);
        asset.setUploader(uploader);
        asset.setAssetCode("ASSET-" + id.toString().substring(0, 8));
        asset.setStorageUrl("https://storage.example/" + id + ".jpg");
        asset.setFileName("robotics-demo.jpg");
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(1024L);
        return asset;
    }

    private static Object[] row(UUID assetId, double score) {
        return new Object[] {assetId.toString(), score};
    }
}
