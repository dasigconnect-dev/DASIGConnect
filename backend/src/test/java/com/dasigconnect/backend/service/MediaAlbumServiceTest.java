package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.media.AlbumAddAssetsRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumGenerateSuggestionsRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumPromptSuggestionRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumResponseDto;
import com.dasigconnect.backend.model.entity.AlbumAsset;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetEmbeddingType;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.MediaImportBatch;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AlbumAssetRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
import com.dasigconnect.backend.repository.MediaAssetEmbeddingRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaImportBatchRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaAlbumServiceTest {

    @Mock private MediaAlbumRepository albumRepository;
    @Mock private AlbumAssetRepository albumAssetRepository;
    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private MediaAssetEmbeddingRepository mediaAssetEmbeddingRepository;
    @Mock private MediaImportBatchRepository mediaImportBatchRepository;
    @Mock private UserRepository userRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private ClaudeVisionClient claudeVisionClient;
    @Mock private AuditLogService auditLogService;

    private MediaAlbumService service;

    private final UUID institutionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private JwtUserDetails user;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new MediaAlbumService(albumRepository, albumAssetRepository, mediaAssetRepository,
                mediaAssetEmbeddingRepository, mediaImportBatchRepository, userRepository,
                institutionRepository, claudeVisionClient, auditLogService);
        user = new JwtUserDetails(userId, "c@x.edu", "CONTRIBUTOR", institutionId);
        actor = mock(User.class);
    }

    private MediaAlbum album(UUID id) {
        MediaAlbum a = new MediaAlbum();
        a.setId(id);
        a.setName("Album");
        a.setSource(MediaAlbum.SOURCE_MANUAL);
        Institution inst = new Institution();
        inst.setId(institutionId);
        a.setInstitution(inst);
        return a;
    }

    private MediaAsset assetInInstitution(UUID id, UUID instId) {
        Institution inst = new Institution();
        inst.setId(instId);
        inst.setName("Institution");
        User uploader = new User();
        uploader.setId(UUID.randomUUID());
        uploader.setEmail("uploader@x.edu");
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setInstitution(inst);
        asset.setUploader(uploader);
        asset.setAssetCode("ASSET-" + id.toString().substring(0, 8));
        asset.setStorageUrl("https://example.test/" + id + ".jpg");
        asset.setFileName(id + ".jpg");
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(1024L);
        return asset;
    }

    private int statusOf(ResponseStatusException ex) {
        return ex.getStatusCode().value();
    }

    @Test
    void create_adminWithoutInstitution_returns400() {
        JwtUserDetails admin = new JwtUserDetails(userId, "a@x.edu", "ADMINISTRATOR", null);
        AlbumCreateRequestDto dto = new AlbumCreateRequestDto();
        dto.setName("Trip");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(dto, admin));
        assertEquals(400, statusOf(ex));
        verify(albumRepository, never()).save(any());
    }

    @Test
    void create_adminWithInstitution_savesAndAudits() {
        JwtUserDetails admin = new JwtUserDetails(userId, "a@x.edu", "ADMINISTRATOR", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(institutionRepository.getReferenceById(institutionId)).thenReturn(mock(Institution.class));
        when(albumRepository.save(any(MediaAlbum.class))).thenAnswer(inv -> {
            MediaAlbum a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AlbumCreateRequestDto dto = new AlbumCreateRequestDto();
        dto.setName("Trip");
        dto.setInstitutionId(institutionId);

        AlbumResponseDto result = service.create(dto, admin);

        assertEquals("Trip", result.getName());
        verify(auditLogService).record(eq(actor), eq("ALBUM_CREATED"), any(), any(), any(), any());
    }

    @Test
    void create_valid_savesAndAudits() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(institutionRepository.getReferenceById(institutionId)).thenReturn(mock(Institution.class));
        when(albumRepository.save(any(MediaAlbum.class))).thenAnswer(inv -> {
            MediaAlbum a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AlbumCreateRequestDto dto = new AlbumCreateRequestDto();
        dto.setName("  Trip 2026  ");

        AlbumResponseDto result = service.create(dto, user);

        assertEquals("Trip 2026", result.getName());
        verify(auditLogService).record(eq(actor), eq("ALBUM_CREATED"), any(), any(), any(), any());
    }

    @Test
    void generateSuggestedFromImportBatch_groupsByAiCategory() {
        UUID batchId = UUID.randomUUID();
        MediaImportBatch batch = new MediaImportBatch();
        batch.setId(batchId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.of(batch));
        when(institutionRepository.getReferenceById(institutionId)).thenReturn(mock(Institution.class));
        when(albumRepository.existsByInstitutionAndSourceAndName(eq(institutionId), eq(MediaAlbum.SOURCE_AI_SUGGESTED), any()))
                .thenReturn(false);
        when(albumRepository.save(any(MediaAlbum.class))).thenAnswer(inv -> {
            MediaAlbum a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        MediaAsset first = assetInInstitution(UUID.randomUUID(), institutionId);
        first.setAiCategory("Awarding");
        MediaAsset second = assetInInstitution(UUID.randomUUID(), institutionId);
        second.setAiCategory("Awarding");
        MediaAsset single = assetInInstitution(UUID.randomUUID(), institutionId);
        single.setAiCategory("Seminar");
        when(mediaAssetRepository.findActiveByImportBatch(batchId, institutionId))
                .thenReturn(List.of(first, second, single));

        AlbumGenerateSuggestionsRequestDto dto = new AlbumGenerateSuggestionsRequestDto();
        dto.setImportBatchId(batchId);

        var response = service.generateSuggestedFromImportBatch(dto, user);

        assertEquals(2, response.getGroupsEvaluated());
        assertEquals(1, response.getAlbumsCreated());
        assertEquals("Awarding - AI suggestion " + batchId.toString().substring(0, 8), response.getAlbums().get(0).getName());
        verify(albumAssetRepository, times(2)).save(any(AlbumAsset.class));
        verify(auditLogService).record(eq(actor), eq("AI_ALBUM_SUGGESTED"), any(), any(), any(), any());
    }

    @Test
    void generateSuggestedFromImportBatch_clustersByImageEmbeddingAndNamesWithClaude() {
        UUID batchId = UUID.randomUUID();
        MediaImportBatch batch = new MediaImportBatch();
        batch.setId(batchId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.of(batch));
        when(institutionRepository.getReferenceById(institutionId)).thenReturn(mock(Institution.class));
        when(albumRepository.existsByInstitutionAndSourceAndName(eq(institutionId), eq(MediaAlbum.SOURCE_AI_SUGGESTED), any()))
                .thenReturn(false);
        when(albumRepository.save(any(MediaAlbum.class))).thenAnswer(inv -> {
            MediaAlbum a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        // Two near-identical image vectors (cosine ~1) cluster together; the third is orthogonal.
        MediaAsset a = assetInInstitution(UUID.randomUUID(), institutionId);
        MediaAsset b = assetInInstitution(UUID.randomUUID(), institutionId);
        MediaAsset c = assetInInstitution(UUID.randomUUID(), institutionId);
        when(mediaAssetRepository.findActiveByImportBatch(batchId, institutionId)).thenReturn(List.of(a, b, c));
        when(mediaAssetEmbeddingRepository.findEmbedding(a.getId(), MediaAssetEmbeddingType.IMAGE))
                .thenReturn(Optional.of("[1,0,0]"));
        when(mediaAssetEmbeddingRepository.findEmbedding(b.getId(), MediaAssetEmbeddingType.IMAGE))
                .thenReturn(Optional.of("[0.99,0.02,0]"));
        when(mediaAssetEmbeddingRepository.findEmbedding(c.getId(), MediaAssetEmbeddingType.IMAGE))
                .thenReturn(Optional.of("[0,1,0]"));
        when(claudeVisionClient.suggestAlbumName(any())).thenReturn("Awarding Ceremony");

        AlbumGenerateSuggestionsRequestDto dto = new AlbumGenerateSuggestionsRequestDto();
        dto.setImportBatchId(batchId);

        var response = service.generateSuggestedFromImportBatch(dto, user);

        assertEquals(1, response.getAlbumsCreated());
        assertEquals("Awarding Ceremony", response.getAlbums().get(0).getName());
        // Only the 2-photo similarity cluster becomes an album; the orthogonal singleton is skipped.
        verify(albumAssetRepository, times(2)).save(any(AlbumAsset.class));
    }

    @Test
    void generateSuggestedFromImportBatch_missingBatch_returns404() {
        UUID batchId = UUID.randomUUID();
        when(mediaImportBatchRepository.findByIdAndInstitution(batchId, institutionId)).thenReturn(Optional.empty());
        AlbumGenerateSuggestionsRequestDto dto = new AlbumGenerateSuggestionsRequestDto();
        dto.setImportBatchId(batchId);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateSuggestedFromImportBatch(dto, user));

        assertEquals(404, statusOf(ex));
        verify(albumRepository, never()).save(any());
    }

    @Test
    void suggestFromPrompt_returnsRankedCandidatesWithoutCreatingAlbum() {
        MediaAsset robotics = assetInInstitution(UUID.randomUUID(), institutionId);
        robotics.setTitle("Robotics Seminar Speakers");
        robotics.setAiDescription("Students listening to guest speakers during a robotics seminar.");
        robotics.setAiTags(new String[] {"robotics", "seminar", "students"});

        MediaAsset sports = assetInInstitution(UUID.randomUUID(), institutionId);
        sports.setTitle("Sports Festival");
        sports.setAiDescription("Outdoor games and team activities.");
        sports.setAiTags(new String[] {"sports", "festival"});

        when(mediaAssetRepository.findActiveByInstitution(institutionId)).thenReturn(List.of(sports, robotics));

        AlbumPromptSuggestionRequestDto dto = new AlbumPromptSuggestionRequestDto();
        dto.setPrompt("robotics seminar with students and speakers");

        var response = service.suggestFromPrompt(dto, user);

        assertEquals("Robotics Seminar Students Speakers Collection", response.getSuggestedName());
        assertEquals(1, response.getTotalCandidates());
        assertEquals(robotics.getId(), response.getCandidates().get(0).getAsset().getId());
        assertEquals("high", response.getCandidates().get(0).getConfidence());
        verify(albumRepository, never()).save(any());
        verify(albumAssetRepository, never()).save(any());
    }

    @Test
    void addAssets_skipsDuplicatesAndAddsNew() {
        UUID albumId = UUID.randomUUID();
        UUID existingAssetId = UUID.randomUUID();
        UUID newAssetId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(albumRepository.findByIdAndInstitution(albumId, institutionId)).thenReturn(Optional.of(album(albumId)));
        when(albumAssetRepository.countByAlbumId(albumId)).thenReturn(0L);
        when(albumAssetRepository.existsByAlbumIdAndAssetId(albumId, existingAssetId)).thenReturn(true);
        when(albumAssetRepository.existsByAlbumIdAndAssetId(albumId, newAssetId)).thenReturn(false);
        MediaAsset newAsset = assetInInstitution(newAssetId, institutionId);
        when(mediaAssetRepository.findActiveById(newAssetId)).thenReturn(Optional.of(newAsset));
        when(albumAssetRepository.save(any(AlbumAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(albumAssetRepository.findByAlbumOrdered(albumId)).thenReturn(List.of());

        AlbumAddAssetsRequestDto dto = new AlbumAddAssetsRequestDto();
        dto.setAssetIds(List.of(existingAssetId, newAssetId));

        service.addAssets(albumId, dto, user);

        verify(albumAssetRepository, times(1)).save(any(AlbumAsset.class)); // only the new one
        verify(auditLogService).record(eq(actor), eq("ALBUM_ASSETS_ADDED"), any(), any(), eq(albumId), any());
    }

    @Test
    void addAssets_assetFromOtherInstitution_returns404() {
        UUID albumId = UUID.randomUUID();
        UUID foreignAssetId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(albumRepository.findByIdAndInstitution(albumId, institutionId)).thenReturn(Optional.of(album(albumId)));
        when(albumAssetRepository.countByAlbumId(albumId)).thenReturn(0L);
        when(albumAssetRepository.existsByAlbumIdAndAssetId(albumId, foreignAssetId)).thenReturn(false);
        MediaAsset foreignAsset = assetInInstitution(foreignAssetId, UUID.randomUUID());
        when(mediaAssetRepository.findActiveById(foreignAssetId)).thenReturn(Optional.of(foreignAsset));

        AlbumAddAssetsRequestDto dto = new AlbumAddAssetsRequestDto();
        dto.setAssetIds(List.of(foreignAssetId));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addAssets(albumId, dto, user));
        assertEquals(404, statusOf(ex));
        verify(albumAssetRepository, never()).save(any());
    }

    @Test
    void removeAsset_deletesAndAudits() {
        UUID albumId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(albumRepository.findByIdAndInstitution(albumId, institutionId)).thenReturn(Optional.of(album(albumId)));

        service.removeAsset(albumId, assetId, user);

        verify(albumAssetRepository).deleteByAlbumIdAndAssetId(albumId, assetId);
        verify(auditLogService).record(eq(actor), eq("ALBUM_ASSET_REMOVED"), any(), any(), eq(albumId), any());
    }

    @Test
    void delete_removesAndAudits() {
        UUID albumId = UUID.randomUUID();
        MediaAlbum existing = album(albumId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(albumRepository.findByIdAndInstitution(albumId, institutionId)).thenReturn(Optional.of(existing));

        service.delete(albumId, user);

        verify(albumRepository).delete(existing);
        verify(auditLogService).record(eq(actor), eq("ALBUM_DELETED"), any(), any(), eq(albumId), any());
    }

    @Test
    void get_notFound_returns404() {
        UUID albumId = UUID.randomUUID();
        when(albumRepository.findByIdAndInstitution(albumId, institutionId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.get(albumId, user));
        assertEquals(404, statusOf(ex));
    }
}
