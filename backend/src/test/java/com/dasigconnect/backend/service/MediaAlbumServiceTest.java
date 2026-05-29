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

import com.dasigconnect.backend.model.dto.media.AlbumAddAssetsRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.AlbumResponseDto;
import com.dasigconnect.backend.model.entity.AlbumAsset;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AlbumAssetRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAlbumRepository;
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
class MediaAlbumServiceTest {

    @Mock private MediaAlbumRepository albumRepository;
    @Mock private AlbumAssetRepository albumAssetRepository;
    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private UserRepository userRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private AuditLogService auditLogService;

    private MediaAlbumService service;

    private final UUID institutionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private JwtUserDetails user;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new MediaAlbumService(albumRepository, albumAssetRepository, mediaAssetRepository,
                userRepository, institutionRepository, auditLogService);
        user = new JwtUserDetails(userId, "c@x.edu", "CONTRIBUTOR", institutionId);
        actor = mock(User.class);
    }

    private MediaAlbum album(UUID id) {
        MediaAlbum a = new MediaAlbum();
        a.setId(id);
        a.setName("Album");
        a.setSource(MediaAlbum.SOURCE_MANUAL);
        return a;
    }

    private MediaAsset assetInInstitution(UUID id, UUID instId) {
        Institution inst = mock(Institution.class);
        when(inst.getId()).thenReturn(instId);
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setInstitution(inst);
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
