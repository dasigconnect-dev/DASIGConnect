package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.media.MediaAssetRightsDto;
import com.dasigconnect.backend.model.dto.media.MediaAssetRightsUpdateRequestDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetRights;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaAssetRightsRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.LocalDate;
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
class MediaRightsServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private MediaAssetRightsRepository rightsRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UserRepository userRepository;

    private MediaRightsService service;

    @BeforeEach
    void setUp() {
        service = new MediaRightsService(
                mediaAssetRepository, rightsRepository, auditLogService, userRepository);
    }

    @Test
    void getRights_noRecord_returnsEmptyIncompleteDto() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));
        when(rightsRepository.findByAssetIdAndInstitutionId(assetId, institutionId)).thenReturn(Optional.empty());

        MediaAssetRightsDto dto = service.getRights(assetId, user("validator", institutionId));

        assertThat(dto.present()).isFalse();
        assertThat(dto.derivedState()).isEqualTo("incomplete");
        assertThat(dto.permitsPublicClearance()).isFalse();
    }

    @Test
    void getRights_crossTenant_isHidden() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));

        assertThatThrownBy(() -> service.getRights(assetId, user("validator", UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void updateRights_upsertsAndAudits_andReportsClearedState() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));
        when(rightsRepository.findByAssetIdAndInstitutionId(assetId, institutionId)).thenReturn(Optional.empty());
        when(rightsRepository.save(any(MediaAssetRights.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaAssetRightsUpdateRequestDto dto = new MediaAssetRightsUpdateRequestDto();
        dto.setRightsHolder("DOST Region 7");
        dto.setRightsBasis("owned");
        dto.setPermittedChannels(List.of("facebook", "  ", "website"));
        dto.setExpiresAt(LocalDate.now().plusYears(1));

        MediaAssetRightsDto result = service.updateRights(assetId, dto, user(actorId, "validator", institutionId));

        assertThat(result.present()).isTrue();
        assertThat(result.derivedState()).isEqualTo("cleared");
        assertThat(result.permitsPublicClearance()).isTrue();
        assertThat(result.permittedChannels()).containsExactly("facebook", "website");
        assertThat(result.verifiedBy()).isEqualTo(actorId);
        verify(rightsRepository).save(any(MediaAssetRights.class));
        verify(auditLogService).record(any(), eq("MEDIA_ASSET_RIGHTS_UPDATED"), any(), any(), eq(assetId), any());
    }

    @Test
    void updateRights_expiredRecord_doesNotPermitClearance() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));
        when(rightsRepository.findByAssetIdAndInstitutionId(assetId, institutionId)).thenReturn(Optional.empty());
        when(rightsRepository.save(any(MediaAssetRights.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaAssetRightsUpdateRequestDto dto = new MediaAssetRightsUpdateRequestDto();
        dto.setRightsHolder("Partner HEI");
        dto.setRightsBasis("licensed");
        dto.setExpiresAt(LocalDate.now().minusDays(1));

        MediaAssetRightsDto result = service.updateRights(assetId, dto, user("admin", null));

        assertThat(result.derivedState()).isEqualTo("expired");
        assertThat(result.permitsPublicClearance()).isFalse();
    }

    @Test
    void updateRights_invalidBasis_returns400() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));

        MediaAssetRightsUpdateRequestDto dto = new MediaAssetRightsUpdateRequestDto();
        dto.setRightsBasis("stolen");

        assertThatThrownBy(() -> service.updateRights(assetId, dto, user("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(rightsRepository, never()).save(any());
    }

    @Test
    void updateRights_expiryBeforeClearance_returns400() {
        UUID assetId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(mediaAssetRepository.findActiveById(assetId)).thenReturn(Optional.of(asset(assetId, institutionId)));

        MediaAssetRightsUpdateRequestDto dto = new MediaAssetRightsUpdateRequestDto();
        dto.setRightsBasis("consent");
        dto.setClearanceDate(LocalDate.now());
        dto.setExpiresAt(LocalDate.now().minusDays(5));

        assertThatThrownBy(() -> service.updateRights(assetId, dto, user("validator", institutionId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(rightsRepository, never()).save(any());
    }

    private static MediaAsset asset(UUID assetId, UUID institutionId) {
        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setAssetCode("ASSET-" + assetId.toString().substring(0, 8));
        Institution institution = new Institution();
        institution.setId(institutionId);
        asset.setInstitution(institution);
        return asset;
    }

    private static JwtUserDetails user(String role, UUID institutionId) {
        return user(UUID.randomUUID(), role, institutionId);
    }

    private static JwtUserDetails user(UUID userId, String role, UUID institutionId) {
        return new JwtUserDetails(userId, role + "@example.edu", role, institutionId);
    }
}
