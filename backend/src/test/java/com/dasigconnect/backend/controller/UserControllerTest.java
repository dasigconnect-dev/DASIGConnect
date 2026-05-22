package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import com.dasigconnect.backend.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    @WithMockUser
    void me_authenticated_returnsProfile() throws Exception {
        UUID institutionId = UUID.randomUUID();
        when(userService.getProfile(any())).thenReturn(UserDto.from(user(
                UUID.randomUUID(), "user@cit.edu.ph", UserRole.contributor, institution(institutionId))));

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@cit.edu.ph"))
                .andExpect(jsonPath("$.role").value("contributor"))
                .andExpect(jsonPath("$.institutionId").value(institutionId.toString()));
    }

    @Test
    void listUsers_withoutRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("institutionId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VALIDATOR")
    void listUsers_asValidator_returnsUsers() throws Exception {
        UUID institutionId = UUID.randomUUID();
        when(userService.listByInstitution(any(), any())).thenReturn(List.of(userDto(
                UUID.randomUUID(), "contributor@cit.edu.ph", UserRole.contributor, institutionId)));

        mockMvc.perform(get("/api/v1/users").param("institutionId", institutionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("contributor@cit.edu.ph"));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void listUsers_asContributor_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("institutionId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void userCounts_asAdministrator_returnsCounts() throws Exception {
        UUID institutionId = UUID.randomUUID();
        when(userService.countByRole(any(), any())).thenReturn(Map.of("contributors", 5L, "validators", 1L));

        mockMvc.perform(get("/api/v1/users/counts").param("institutionId", institutionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contributors").value(5))
                .andExpect(jsonPath("$.validators").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void userCounts_missingInstitutionId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/counts"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "VALIDATOR")
    void getUser_asValidator_returnsUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(userService.getById(any(), any())).thenReturn(userDto(
                userId, "contributor@cit.edu.ph", UserRole.contributor, institutionId));

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("contributor@cit.edu.ph"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void updateStatus_asAdministrator_returnsUpdatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        User inactive = user(userId, "contributor@cit.edu.ph", UserRole.contributor, institution(institutionId));
        inactive.setAccountState(UserStatus.inactive);
        when(userService.updateStatus(any(), any(), any())).thenReturn(UserDto.from(inactive));

        mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {"accountState":"inactive"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("inactive"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void updateStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static UserDto userDto(UUID id, String email, UserRole role, UUID institutionId) {
        return UserDto.from(user(id, email, role, institution(institutionId)));
    }

    private static User user(UUID id, String email, UserRole role, Institution institution) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setAccountState(UserStatus.active);
        user.setInstitution(institution);
        return user;
    }

    private static Institution institution(UUID id) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName("CIT-U");
        institution.setCode("CIT-U");
        institution.setEmailDomain("cit.edu.ph");
        return institution;
    }
}
