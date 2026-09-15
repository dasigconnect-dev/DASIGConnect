package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.MediaStorageService;
import com.dasigconnect.backend.service.TenantScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No auth is required to reach this endpoint — mirrors the R2 public URL it replaces. */
@WebMvcTest(MediaProxyController.class)
@Import(SecurityConfig.class)
class MediaProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaStorageService mediaStorageService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    void serve_knownObject_returnsBytesUnauthenticated() throws Exception {
        when(mediaStorageService.downloadObject("media/inst-1/asset-1/photo.jpg"))
                .thenReturn(new MediaStorageService.StoredObject(new byte[] {1, 2, 3}, "image/jpeg", 3L));

        mockMvc.perform(get("/api/v1/media-files/media/inst-1/asset-1/photo.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void serve_unknownObject_returns404() throws Exception {
        when(mediaStorageService.downloadObject("media/missing.png"))
                .thenThrow(new MediaStorageService.MediaObjectNotFoundException("media/missing.png"));

        mockMvc.perform(get("/api/v1/media-files/media/missing.png"))
                .andExpect(status().isNotFound());
    }
}
