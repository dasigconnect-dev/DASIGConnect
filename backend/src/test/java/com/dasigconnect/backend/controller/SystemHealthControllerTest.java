package com.dasigconnect.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.systemhealth.HealthStatus;
import com.dasigconnect.backend.model.dto.systemhealth.SystemHealthSummaryDto;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.SystemHealthService;
import com.dasigconnect.backend.service.TenantScopeService;
import com.dasigconnect.backend.service.TokenManagementService;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemHealthController.class)
@Import(SecurityConfig.class)
class SystemHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemHealthService systemHealthService;

    @MockitoBean
    private TokenManagementService tokenManagementService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    void summary_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/system-health/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void summary_asAdministrator_returnsSystemHealthPayload() throws Exception {
        when(systemHealthService.summary()).thenReturn(new SystemHealthSummaryDto(
                Instant.parse("2026-08-27T00:00:00Z"),
                HealthStatus.WARNING,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                0,
                0));

        mockMvc.perform(get("/api/v1/system-health/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").value("WARNING"))
                .andExpect(jsonPath("$.data.warningCount").value(1));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMINISTRATOR")
    void export_asSuperAdministrator_returnsCsvAttachment() throws Exception {
        when(systemHealthService.exportSnapshotCsv()).thenReturn("section,metric,status,value,unit,detail\n");

        mockMvc.perform(get("/api/v1/system-health/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("DASIGConnect_System_Health_")));
    }
}
