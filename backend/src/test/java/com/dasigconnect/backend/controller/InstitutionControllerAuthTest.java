package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.service.InstitutionService;
import com.dasigconnect.backend.service.JWTService;
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

@WebMvcTest(InstitutionController.class)
@Import(SecurityConfig.class)
class InstitutionControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstitutionService institutionService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_canListInstitutions() throws Exception {
        when(institutionService.listInstitutions()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/institutions")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_cannotCreateInstitution() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test University\",\"institutionCode\":\"TEST-U\",\"emailDomain\":\"test.edu.ph\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_cannotDeactivateInstitution() throws Exception {
        mockMvc.perform(patch("/api/v1/institutions/{id}/deactivate", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void moderator_cannotDeleteInstitution() throws Exception {
        mockMvc.perform(delete("/api/v1/institutions/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canDeactivateInstitution() throws Exception {
        when(institutionService.deactivateInstitution(any())).thenReturn(null);
        mockMvc.perform(patch("/api/v1/institutions/{id}/deactivate", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
