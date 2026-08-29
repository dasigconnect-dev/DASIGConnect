package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.audit.AuditEntityType;
import com.dasigconnect.backend.model.dto.audit.AuditLogCategory;
import com.dasigconnect.backend.model.dto.audit.AuditLogDto;
import com.dasigconnect.backend.service.AuditLogService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditLogController.class)
@Import(SecurityConfig.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    void getAuditLogs_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/audit-log"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAuditLogs_asModerator_returnsPage() throws Exception {
        UUID logId = UUID.randomUUID();
        AuditLogDto dto = new AuditLogDto(
                logId,
                Instant.parse("2026-08-27T10:00:00Z"),
                "SUBMISSION_APPROVED",
                "approved",
                AuditLogCategory.APPROVAL,
                "Approvals & Direct Posts",
                new AuditLogDto.ActorDto(UUID.randomUUID(), "Admin John", "admin@dasig.gov.ph", "MODERATOR", null, "DOST Region 7"),
                new AuditLogDto.EntityRefDto(UUID.randomUUID(), AuditEntityType.SUBMISSION, "Submission", "Tech Expo 2026", true, "/submissions"),
                new AuditLogDto.ClientInfoDto("192.168.1.1", "Mozilla/5.0"),
                "Approved submission Tech Expo 2026",
                Map.of(),
                "{}",
                List.of()
        );

        when(auditLogService.searchAuditLogs(any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(logId.toString()))
                .andExpect(jsonPath("$.data.content[0].action").value("SUBMISSION_APPROVED"))
                .andExpect(jsonPath("$.data.content[0].category").value("APPROVAL"))
                .andExpect(jsonPath("$.data.content[0].actor.name").value("Admin John"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCategories_returnsOptions() throws Exception {
        when(auditLogService.getMetadataOptions()).thenReturn(Map.of(
                "categories", List.of(Map.of("key", "APPROVAL", "label", "Approvals & Direct Posts")),
                "entityTypes", List.of(Map.of("key", "SUBMISSION", "label", "Submission"))
        ));

        mockMvc.perform(get("/api/v1/audit-log/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categories[0].key").value("APPROVAL"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportCsv_returnsCsvAttachment() throws Exception {
        when(auditLogService.exportAuditLogsCsv(any()))
                .thenReturn("Log ID,Timestamp (PHT),Actor Name,Action Type\n1,2026-08-27 18:00:00,Admin,APPROVED\n");

        mockMvc.perform(get("/api/v1/audit-log/export-csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("DASIGConnect_AuditLog_")));
    }
}
