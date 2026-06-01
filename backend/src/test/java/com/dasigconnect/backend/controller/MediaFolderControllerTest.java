package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.media.FolderResponseDto;
import com.dasigconnect.backend.model.entity.MediaFolder;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.MediaFolderService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MediaFolderController.class)
@Import(SecurityConfig.class)
class MediaFolderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MediaFolderService mediaFolderService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    private FolderResponseDto folderDto(String name) {
        MediaFolder f = new MediaFolder();
        f.setId(UUID.randomUUID());
        f.setName(name);
        return FolderResponseDto.from(f, 2, 1);
    }

    @Test
    void list_withoutAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/media-folders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void list_authenticated_returnsFolders() throws Exception {
        when(mediaFolderService.list(any(), any())).thenReturn(List.of(folderDto("Events")));
        mockMvc.perform(get("/api/v1/media-folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Events"))
                .andExpect(jsonPath("$[0].assetCount").value(2));
    }

    @Test
    @WithMockUser
    void create_authenticated_returns201() throws Exception {
        when(mediaFolderService.create(any(), any())).thenReturn(folderDto("Events"));
        mockMvc.perform(post("/api/v1/media-folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Events\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Events"));
    }

    @Test
    @WithMockUser
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/media-folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void rename_authenticated_returns200() throws Exception {
        when(mediaFolderService.rename(any(), any(), any())).thenReturn(folderDto("Renamed"));
        mockMvc.perform(patch("/api/v1/media-folders/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    @WithMockUser
    void delete_authenticated_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/media-folders/{id}", id))
                .andExpect(status().isNoContent());
        verify(mediaFolderService).delete(eq(id), any());
    }
}
