package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.MediaIntegrityStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AssetTagRepository;
import com.dasigconnect.backend.repository.MediaAssetRelationRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaAssetRightsRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.MediaRepositoryExportService.ExportPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaRepositoryExportServiceTest {

    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private MediaAssetRightsRepository rightsRepository;
    @Mock private MediaAssetRelationRepository relationRepository;
    @Mock private AssetTagRepository assetTagRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private UserRepository userRepository;

    private MediaRepositoryExportService service;

    @BeforeEach
    void setUp() {
        service = new MediaRepositoryExportService(
                mediaAssetRepository, rightsRepository, relationRepository, assetTagRepository,
                auditLogService, userRepository, new ObjectMapper());
    }

    @Test
    void export_csv_hasHeaders_assetCode_andNoStorageUrl() {
        MediaAsset asset = asset();
        when(mediaAssetRepository.findAllActive()).thenReturn(List.of(asset));
        when(assetTagRepository.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(rightsRepository.findByAssetIdIn(anyList())).thenReturn(List.of());
        when(relationRepository.findForExport(isNull())).thenReturn(List.of());

        ExportPayload payload = service.export("csv", null, admin());

        assertThat(payload.contentType()).isEqualTo("text/csv");
        assertThat(payload.filename()).contains("network").endsWith(".csv");
        assertThat(payload.body()).contains("dc_identifier,dc_title");
        assertThat(payload.body()).contains(asset.getAssetCode());
        assertThat(payload.body()).doesNotContain("super-secret-signed-url");
        verify(auditLogService).record(any(), eq("MEDIA_REPOSITORY_EXPORTED"), any(), any(), any(), any());
    }

    @Test
    void export_json_wrapsWithMappingAndCount() {
        UUID institutionId = UUID.randomUUID();
        MediaAsset asset = asset();
        when(mediaAssetRepository.findActiveByInstitution(institutionId)).thenReturn(List.of(asset));
        when(assetTagRepository.findLabelsAndSourcesByMediaAssetIds(anyList())).thenReturn(List.of());
        when(rightsRepository.findByAssetIdIn(anyList())).thenReturn(List.of());
        when(relationRepository.findForExport(institutionId)).thenReturn(List.of());

        ExportPayload payload = service.export("json", institutionId, admin());

        assertThat(payload.contentType()).isEqualTo("application/json");
        assertThat(payload.body()).contains("\"dublinCoreMapping\"");
        assertThat(payload.body()).contains("\"count\" : 1");
        assertThat(payload.body()).contains("\"scope\" : \"institution\"");
        assertThat(payload.body()).doesNotContain("super-secret-signed-url");
    }

    @Test
    void export_invalidFormat_returns400() {
        assertThatThrownBy(() -> service.export("xml", null, admin()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(mediaAssetRepository, never()).findAllActive();
    }

    @Test
    void export_validatorOtherInstitution_returns403() {
        JwtUserDetails validator =
                new JwtUserDetails(UUID.randomUUID(), "v@example.edu", "validator", UUID.randomUUID());

        assertThatThrownBy(() -> service.export("csv", UUID.randomUUID(), validator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(mediaAssetRepository, never()).findActiveByInstitution(any());
    }

    private static MediaAsset asset() {
        MediaAsset a = new MediaAsset();
        a.setId(UUID.randomUUID());
        a.setAssetCode("ASSET-EXPORT1");
        a.setStorageUrl("https://storage.example/super-secret-signed-url.jpg?token=abc");
        a.setFileName("event.jpg");
        a.setFileType(MediaFileType.jpeg);
        a.setFileSizeBytes(4096);
        a.setVisibility("cleared_for_public");
        a.setStatus(MediaAssetStatus.READY);
        a.setIntegrityStatus(MediaIntegrityStatus.VERIFIED);
        a.setContentSha256("a".repeat(64));
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());
        institution.setName("CIT-U");
        institution.setCode("CITU");
        a.setInstitution(institution);
        User uploader = new User();
        uploader.setId(UUID.randomUUID());
        uploader.setEmail("uploader@example.edu");
        a.setUploader(uploader);
        return a;
    }

    private static JwtUserDetails admin() {
        return new JwtUserDetails(UUID.randomUUID(), "admin@example.edu", "administrator", null);
    }
}
