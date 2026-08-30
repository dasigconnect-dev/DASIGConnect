package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.user.AdminTransferResponseDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.data.email").value("user@cit.edu.ph"))
                .andExpect(jsonPath("$.data.firstName").value("Test"))
                .andExpect(jsonPath("$.data.lastName").value("User"))
                .andExpect(jsonPath("$.data.displayName").value("Test User"))
                .andExpect(jsonPath("$.data.role").value("contributor"))
                .andExpect(jsonPath("$.data.institutionId").value(institutionId.toString()));
    }

    @Test
    void listUsers_withoutRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("institutionId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void listUsers_asModerator_returnsUsers() throws Exception {
        // Moderators may view the contributor roster; they just cannot mutate it.
        UUID institutionId = UUID.randomUUID();
        when(userService.listByInstitution(any(), any())).thenReturn(List.of(userDto(
                UUID.randomUUID(), "contributor@cit.edu.ph", UserRole.contributor, institutionId)));

        mockMvc.perform(get("/api/v1/users").param("institutionId", institutionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("contributor@cit.edu.ph"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listModerators_asAdmin_returnsModerators() throws Exception {
        when(userService.listModerators(any())).thenReturn(List.of(userDto(
                UUID.randomUUID(), "admin@dasigconnect.com", UserRole.moderator, null)));

        mockMvc.perform(get("/api/v1/users/moderators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("admin@dasigconnect.com"))
                .andExpect(jsonPath("$.data[0].role").value("moderator"));
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void listUsers_asContributor_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("institutionId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void userCounts_asAdmin_returnsCounts() throws Exception {
        UUID institutionId = UUID.randomUUID();
        when(userService.countByRole(any(), any())).thenReturn(Map.of("contributors", 5L, "moderators", 1L));

        mockMvc.perform(get("/api/v1/users/counts").param("institutionId", institutionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contributors").value(5))
                .andExpect(jsonPath("$.data.moderators").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void userCounts_missingInstitutionId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/counts"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void getUser_asModerator_returnsUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(userService.getById(any(), any())).thenReturn(userDto(
                userId, "contributor@cit.edu.ph", UserRole.contributor, institutionId));

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("contributor@cit.edu.ph"));
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void updateStatus_asModerator_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountState\":\"inactive\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_asAdmin_returnsUpdatedUser() throws Exception {
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
                .andExpect(jsonPath("$.data.accountState").value("inactive"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_asAdmin_returnsUpdatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User promoted = user(userId, "c@cit.edu.ph", UserRole.moderator, null);
        when(userService.changeRole(any(), any(), any(), any())).thenReturn(UserDto.from(promoted));

        mockMvc.perform(patch("/api/v1/users/{id}/role", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"moderator"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("moderator"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeRole_missingRole_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/role", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void erasePersonalData_asAdmin_returnsSummary() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.erasePersonalData(any(), any()))
                .thenReturn(new com.dasigconnect.backend.service.UserService.ErasureResult(
                        "deleted+" + userId + "@deleted.invalid", 3));

        mockMvc.perform(post("/api/v1/users/{id}/erase", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaAssetsPurged").value(3))
                .andExpect(jsonPath("$.data.anonymizedEmail").value("deleted+" + userId + "@deleted.invalid"));
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void erasePersonalData_asModerator_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/users/{id}/erase", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeUser_withoutRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void removeUser_asModerator_reachesService() throws Exception {
        // The controller now admits moderators; ownership is enforced in the service.
        when(userService.removeUser(any(), any())).thenReturn("deleted");

        mockMvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("deleted"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void requestAdminTransfer_asAdmin_returnsPendingTransfer() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(userService.requestAdminTransfer(any(), any()))
                .thenReturn(new AdminTransferResponseDto(
                        targetId,
                        requesterId,
                        java.time.Instant.now().plusSeconds(3600),
                        "pending_confirmation"));

        mockMvc.perform(post("/api/v1/users/{id}/admin-transfer", targetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetUserId").value(targetId.toString()))
                .andExpect(jsonPath("$.data.requestedBy").value(requesterId.toString()))
                .andExpect(jsonPath("$.data.status").value("pending_confirmation"));
    }

    @Test
    @WithMockUser(roles = "MODERATOR")
    void confirmAdminTransfer_asModerator_returnsIncomingAdmin() throws Exception {
        User incoming = user(UUID.randomUUID(), "incoming@dasigconnect.com", UserRole.moderator, null);
        incoming.setAdminOwner(true);
        when(userService.confirmAdminTransfer(any())).thenReturn(UserDto.from(incoming));

        mockMvc.perform(post("/api/v1/users/admin-transfer/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("incoming@dasigconnect.com"))
                .andExpect(jsonPath("$.data.adminOwner").value(true));
    }

    private static UserDto userDto(UUID id, String email, UserRole role, UUID institutionId) {
        return UserDto.from(user(id, email, role, institution(institutionId)));
    }

    private static User user(UUID id, String email, UserRole role, Institution institution) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
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
