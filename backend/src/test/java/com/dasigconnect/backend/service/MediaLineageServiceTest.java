package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.media.MediaAssetLineageDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetRelationCreateRequestDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetRelation;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.MediaAssetRelationRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
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
class MediaLineageServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private MediaAssetRelationRepository relationRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UserRepository userRepository;

    private MediaLineageService service;

    @BeforeEach
    void setUp() {
        service = new MediaLineageService(
                mediaAssetRepository, relationRepository, auditLogService, userRepository);
    }

    @Test
    void getLineage_returnsParentsAndChildren() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));
        when(mediaAssetRepository.findActiveById(parentId)).thenReturn(Optional.of(asset(parentId, institutionId)));
        when(mediaAssetRepository.findActiveById(childId)).thenReturn(Optional.of(asset(childId, institutionId)));
        when(relationRepository.findByChild(assetId, institutionId))
                .thenReturn(List.of(relation(parentId, assetId, "derived_from", institutionId)));
        when(relationRepository.findByParent(assetId, institutionId))
                .thenReturn(List.of(relation(assetId, childId, "new_version", institutionId)));

        MediaAssetLineageDto lineage = service.getLineage(assetId, user("validator", institutionId));

        assertThat(lineage.parents()).hasSize(1);
        assertThat(lineage.parents().get(0).relationType()).isEqualTo("derived_from");
        assertThat(lineage.parents().get(0).asset().getId()).isEqualTo(parentId);
        assertThat(lineage.children()).hasSize(1);
        assertThat(lineage.children().get(0).asset().getId()).isEqualTo(childId);
    }

    @Test
    void createRelation_linksAndAudits() {
        UUID institutionId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(parentId)).thenReturn(Optional.of(asset(parentId, institutionId)));
        when(mediaAssetRepository.findActiveById(childId)).thenReturn(Optional.of(asset(childId, institutionId)));
        when(relationRepository.existsByParentAssetIdAndChildAssetIdAndRelationType(parentId, childId, "new_version"))
                .thenReturn(false);
        when(relationRepository.findChildIds(childId)).thenReturn(List.of());
        when(relationRepository.findByParent(parentId, institutionId))
                .thenReturn(List.of(relation(parentId, childId, "new_version", institutionId)));
        when(relationRepository.findByChild(parentId, institutionId)).thenReturn(List.of());

        MediaAssetRelationCreateRequestDto dto = new MediaAssetRelationCreateRequestDto();
        dto.setChildAssetId(childId);
        dto.setRelationType("new_version");

        MediaAssetLineageDto lineage = service.createRelation(parentId, dto, user("validator", institutionId));

        assertThat(lineage.children()).hasSize(1);
        verify(relationRepository).save(any(MediaAssetRelation.class));
        verify(auditLogService).record(any(), eq("MEDIA_ASSET_RELATION_ADDED"), any(), any(), eq(parentId), any());
    }

    @Test
    void createRelation_selfLink_returns400() {
        UUID institutionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));

        MediaAssetRelationCreateRequestDto dto = new MediaAssetRelationCreateRequestDto();
        dto.setChildAssetId(assetId);
        dto.setRelationType("new_version");

        assertThatThrownBy(() -> service.createRelation(assetId, dto, user("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(relationRepository, never()).save(any());
    }

    @Test
    void createRelation_invalidType_returns400() {
        UUID institutionId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(parentId)).thenReturn(Optional.of(asset(parentId, institutionId)));

        MediaAssetRelationCreateRequestDto dto = new MediaAssetRelationCreateRequestDto();
        dto.setChildAssetId(UUID.randomUUID());
        dto.setRelationType("forked");

        assertThatThrownBy(() -> service.createRelation(parentId, dto, user("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(relationRepository, never()).save(any());
    }

    @Test
    void createRelation_crossInstitution_returns400() {
        UUID institutionId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(parentId)).thenReturn(Optional.of(asset(parentId, institutionId)));
        when(mediaAssetRepository.findActiveById(childId)).thenReturn(Optional.of(asset(childId, UUID.randomUUID())));

        MediaAssetRelationCreateRequestDto dto = new MediaAssetRelationCreateRequestDto();
        dto.setChildAssetId(childId);
        dto.setRelationType("derived_from");

        assertThatThrownBy(() -> service.createRelation(parentId, dto, user("admin", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(relationRepository, never()).save(any());
    }

    @Test
    void createRelation_duplicate_returns409() {
        UUID institutionId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(parentId)).thenReturn(Optional.of(asset(parentId, institutionId)));
        when(mediaAssetRepository.findActiveById(childId)).thenReturn(Optional.of(asset(childId, institutionId)));
        when(relationRepository.existsByParentAssetIdAndChildAssetIdAndRelationType(parentId, childId, "new_version"))
                .thenReturn(true);

        MediaAssetRelationCreateRequestDto dto = new MediaAssetRelationCreateRequestDto();
        dto.setChildAssetId(childId);
        dto.setRelationType("new_version");

        assertThatThrownBy(() -> service.createRelation(parentId, dto, user("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(relationRepository, never()).save(any());
    }

    @Test
    void createRelation_cycle_returns409() {
        UUID institutionId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(parentId)).thenReturn(Optional.of(asset(parentId, institutionId)));
        when(mediaAssetRepository.findActiveById(childId)).thenReturn(Optional.of(asset(childId, institutionId)));
        when(relationRepository.existsByParentAssetIdAndChildAssetIdAndRelationType(parentId, childId, "new_version"))
                .thenReturn(false);
        // child already reaches parent forward (child -> parent), so parent -> child closes a loop.
        when(relationRepository.findChildIds(childId)).thenReturn(List.of(parentId));

        MediaAssetRelationCreateRequestDto dto = new MediaAssetRelationCreateRequestDto();
        dto.setChildAssetId(childId);
        dto.setRelationType("new_version");

        assertThatThrownBy(() -> service.createRelation(parentId, dto, user("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(relationRepository, never()).save(any());
    }

    @Test
    void getLineage_crossTenant_isHidden() {
        UUID assetId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.getLineage(assetId, user("validator", UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private static MediaAsset asset(UUID id, UUID institutionId) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setAssetCode("ASSET-" + id.toString().substring(0, 8));
        asset.setStorageUrl("https://storage.example/" + id + ".jpg");
        asset.setFileName(id + ".jpg");
        asset.setFileType(MediaFileType.jpeg);
        asset.setFileSizeBytes(2048);
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setName("Institution");
        institution.setCode("INST");
        asset.setInstitution(institution);
        User uploader = new User();
        uploader.setId(UUID.randomUUID());
        uploader.setEmail("uploader@example.edu");
        asset.setUploader(uploader);
        return asset;
    }

    private static MediaAssetRelation relation(UUID parentId, UUID childId, String type, UUID institutionId) {
        MediaAssetRelation relation = new MediaAssetRelation();
        relation.setId(UUID.randomUUID());
        relation.setInstitutionId(institutionId);
        relation.setParentAssetId(parentId);
        relation.setChildAssetId(childId);
        relation.setRelationType(type);
        return relation;
    }

    private static JwtUserDetails user(String role, UUID institutionId) {
        return new JwtUserDetails(UUID.randomUUID(), role + "@example.edu", role, institutionId);
    }
}
