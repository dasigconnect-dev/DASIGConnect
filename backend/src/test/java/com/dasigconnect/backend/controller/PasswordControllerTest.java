package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.PasswordService;
import com.dasigconnect.backend.service.TenantScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.dasigconnect.backend.config.SecurityConfig;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PasswordController.class)
@Import(SecurityConfig.class)
class PasswordControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PasswordService passwordService;
    @MockitoBean JWTService jwtService;
    @MockitoBean TenantScopeService tenantScopeService;

    @Test
    void forgotPassword_validEmail_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void forgotPassword_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void forgotPassword_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_validBody_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"sometoken","newPassword":"newpassword123"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetPassword_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"sometoken","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    @Test
    void resetPassword_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"newpassword123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.token").exists());
    }
}
