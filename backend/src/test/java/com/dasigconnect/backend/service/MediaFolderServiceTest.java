package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.media.FolderCreateRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderMoveRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderRenameRequestDto;
import com.dasigconnect.backend.model.dto.media.FolderResponseDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaFolder;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaFolderRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaFolderServiceTest {

    @Mock private MediaFolderRepository folderRepository;
    @Mock private MediaAssetRepository mediaAssetRepository;
    @Mock private UserRepository userRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private AuditLogService auditLogService;

    private MediaFolderService service;

    private final UUID institutionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private JwtUserDetails user;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new MediaFolderService(folderRepository, mediaAssetRepository,
                userRepository, institutionRepository, auditLogService);
        user = new JwtUserDetails(userId, "c@x.edu", "CONTRIBUTOR", institutionId);
        actor = mock(User.class);
    }

    private MediaFolder folder(UUID id, MediaFolder parent) {
        MediaFolder f = new MediaFolder();
        f.setId(id);
        f.setName("Folder " + id.toString().substring(0, 4));
        f.setParentFolder(parent);
        return f;
    }

    private int statusOf(ResponseStatusException ex) {
        return ex.getStatusCode().value();
    }

    @Test
    void create_adminWithoutInstitution_returns400() {
        JwtUserDetails admin = new JwtUserDetails(userId, "a@x.edu", "ADMINISTRATOR", null);
        FolderCreateRequestDto dto = new FolderCreateRequestDto();
        dto.setName("Events");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(dto, admin));
        assertEquals(400, statusOf(ex));
        verify(folderRepository, never()).save(any());
    }

    @Test
    void create_adminWithInstitution_savesAndAudits() {
        JwtUserDetails admin = new JwtUserDetails(userId, "a@x.edu", "ADMINISTRATOR", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(institutionRepository.getReferenceById(institutionId)).thenReturn(mock(Institution.class));
        when(folderRepository.save(any(MediaFolder.class))).thenAnswer(inv -> {
            MediaFolder f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(mediaAssetRepository.countByFolderIdAndDeletedAtIsNull(any())).thenReturn(0L);
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);

        FolderCreateRequestDto dto = new FolderCreateRequestDto();
        dto.setName("Events");
        dto.setInstitutionId(institutionId);

        FolderResponseDto result = service.create(dto, admin);

        assertEquals("Events", result.getName());
        verify(folderRepository).save(any(MediaFolder.class));
        verify(auditLogService).record(eq(actor), eq("FOLDER_CREATED"), any(), any(), any(), any());
    }

    @Test
    void create_missingParent_returns404() {
        UUID parentId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(folderRepository.findByIdAndInstitution(parentId, institutionId)).thenReturn(Optional.empty());

        FolderCreateRequestDto dto = new FolderCreateRequestDto();
        dto.setName("Sub");
        dto.setParentFolderId(parentId);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(dto, user));
        assertEquals(404, statusOf(ex));
        verify(folderRepository, never()).save(any());
    }

    @Test
    void create_valid_savesAndAudits() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(institutionRepository.getReferenceById(institutionId)).thenReturn(mock(Institution.class));
        when(folderRepository.save(any(MediaFolder.class))).thenAnswer(inv -> {
            MediaFolder f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(mediaAssetRepository.countByFolderIdAndDeletedAtIsNull(any())).thenReturn(0L);
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);

        FolderCreateRequestDto dto = new FolderCreateRequestDto();
        dto.setName("  Events  ");

        FolderResponseDto result = service.create(dto, user);

        assertEquals("Events", result.getName());
        verify(folderRepository).save(any(MediaFolder.class));
        verify(auditLogService).record(eq(actor), eq("FOLDER_CREATED"), any(), any(), any(), any());
    }

    @Test
    void rename_updatesNameAndAudits() {
        UUID id = UUID.randomUUID();
        MediaFolder existing = folder(id, null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(folderRepository.findByIdAndInstitution(id, institutionId)).thenReturn(Optional.of(existing));
        when(folderRepository.save(any(MediaFolder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaAssetRepository.countByFolderIdAndDeletedAtIsNull(any())).thenReturn(0L);
        when(folderRepository.countByParentFolderId(any())).thenReturn(0L);

        FolderRenameRequestDto dto = new FolderRenameRequestDto();
        dto.setName("Renamed");

        FolderResponseDto result = service.rename(id, dto, user);

        assertEquals("Renamed", result.getName());
        verify(auditLogService).record(eq(actor), eq("FOLDER_RENAMED"), any(), any(), eq(id), any());
    }

    @Test
    void move_intoSelf_returns409() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(folderRepository.findByIdAndInstitution(id, institutionId)).thenReturn(Optional.of(folder(id, null)));

        FolderMoveRequestDto dto = new FolderMoveRequestDto();
        dto.setParentFolderId(id);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.move(id, dto, user));
        assertEquals(409, statusOf(ex));
        verify(folderRepository, never()).save(any());
    }

    @Test
    void move_intoOwnDescendant_returns409() {
        UUID parentId = UUID.randomUUID();   // folder A being moved
        UUID childId = UUID.randomUUID();    // folder B, child of A
        MediaFolder a = folder(parentId, null);
        MediaFolder b = folder(childId, a);  // b.parent = a

        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(folderRepository.findByIdAndInstitution(parentId, institutionId)).thenReturn(Optional.of(a));
        when(folderRepository.findByIdAndInstitution(childId, institutionId)).thenReturn(Optional.of(b));

        FolderMoveRequestDto dto = new FolderMoveRequestDto();
        dto.setParentFolderId(childId); // move A under its own descendant B

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.move(parentId, dto, user));
        assertEquals(409, statusOf(ex));
        verify(folderRepository, never()).save(any());
    }

    @Test
    void delete_removesAndAudits() {
        UUID id = UUID.randomUUID();
        MediaFolder existing = folder(id, null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(folderRepository.findByIdAndInstitution(id, institutionId)).thenReturn(Optional.of(existing));

        service.delete(id, user);

        verify(folderRepository).delete(existing);
        verify(auditLogService).record(eq(actor), eq("FOLDER_DELETED"), any(), any(), eq(id), any());
    }
}
