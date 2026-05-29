package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.media.AlbumDetailDto;
import com.dasigconnect.backend.model.dto.media.AlbumResponseDto;
import com.dasigconnect.backend.model.entity.MediaAlbum;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.MediaAlbumService;
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

@WebMvcTest(MediaAlbumController.class)
@Import(SecurityConfig.class)
class MediaAlbumControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MediaAlbumService mediaAlbumService;
    @MockitoBean private JWTService jwtService;
    @MockitoBean private TenantScopeService tenantScopeService;

    private MediaAlbum album(String name) {
        MediaAlbum a = new MediaAlbum();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setSource(MediaAlbum.SOURCE_MANUAL);
        return a;
    }

    @Test
    void list_withoutAuth_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/media-albums"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void create_authenticated_returns201() throws Exception {
        when(mediaAlbumService.create(any(), any())).thenReturn(AlbumResponseDto.from(album("Trip"), 0));
        mockMvc.perform(post("/api/v1/media-albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trip\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Trip"))
                .andExpect(jsonPath("$.source").value("manual"));
    }

    @Test
    @WithMockUser
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/media-albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void get_authenticated_returnsDetail() throws Exception {
        when(mediaAlbumService.get(any(), any())).thenReturn(AlbumDetailDto.from(album("Trip"), List.of()));
        mockMvc.perform(get("/api/v1/media-albums/{id}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trip"))
                .andExpect(jsonPath("$.assetCount").value(0));
    }

    @Test
    @WithMockUser
    void addAssets_authenticated_returns200() throws Exception {
        when(mediaAlbumService.addAssets(any(), any(), any()))
                .thenReturn(AlbumDetailDto.from(album("Trip"), List.of()));
        mockMvc.perform(post("/api/v1/media-albums/{id}/assets", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void addAssets_emptyList_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/media-albums/{id}/assets", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void removeAsset_authenticated_returns204() throws Exception {
        UUID albumId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/media-albums/{id}/assets/{assetId}", albumId, assetId))
                .andExpect(status().isNoContent());
        verify(mediaAlbumService).removeAsset(eq(albumId), eq(assetId), any());
    }

    @Test
    @WithMockUser
    void delete_authenticated_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/media-albums/{id}", id))
                .andExpect(status().isNoContent());
        verify(mediaAlbumService).delete(eq(id), any());
    }
}
