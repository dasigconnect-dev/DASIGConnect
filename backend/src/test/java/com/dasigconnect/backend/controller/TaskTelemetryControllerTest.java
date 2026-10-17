package com.dasigconnect.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TaskTelemetryService;
import com.dasigconnect.backend.service.TaskTelemetryService.NextActionDto;
import com.dasigconnect.backend.service.TaskTelemetryService.RoleTaskSummaryDto;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskTelemetryDto;
import com.dasigconnect.backend.service.TenantScopeService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskTelemetryController.class)
@Import(SecurityConfig.class)
class TaskTelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskTelemetryService taskTelemetryService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    private UsernamePasswordAuthenticationToken authFor(String role) {
        JwtUserDetails principal = new JwtUserDetails(
                UUID.randomUUID(), role + "@dasig.org", role, UUID.randomUUID());
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }

    @Test
    void nextAction_returnsRoleSpecificNextAction() throws Exception {
        when(taskTelemetryService.getNextAction(any()))
                .thenReturn(new NextActionDto(
                        "draft",
                        "Finish your draft — complete required items and submit",
                        1L,
                        "/submissions/new?id=11111111-1111-1111-1111-111111111111"));

        mockMvc.perform(get("/api/v1/me/next-action").with(authentication(authFor("contributor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("draft"))
                .andExpect(jsonPath("$.data.label").value("Finish your draft — complete required items and submit"));
    }

    @Test
    void recordTaskEvent_recordsTelemetryEvent() throws Exception {
        UUID logId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        when(taskTelemetryService.recordTaskEvent(any(), any()))
                .thenReturn(new TaskTelemetryDto(
                        logId, "contributor", "CREATE_AND_SUBMIT_POST", subId,
                        Instant.now(), Instant.now(), 12500L, 1, true, 0, "COMPLETED"));

        mockMvc.perform(post("/api/v1/task-events")
                        .with(authentication(authFor("contributor")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"CREATE_AND_SUBMIT_POST\",\"submissionId\":\"" + subId + "\",\"event\":\"completed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.firstAttemptSuccess").value(true));
    }

    @Test
    void summary_forModerator_returnsRoleSummaries() throws Exception {
        when(taskTelemetryService.getSummary())
                .thenReturn(List.of(new RoleTaskSummaryDto("contributor", 5L, 5L, 100.0, 42.5)));

        mockMvc.perform(get("/api/v1/telemetry/tasks/summary").with(authentication(authFor("moderator"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userRole").value("contributor"))
                .andExpect(jsonPath("$.data[0].firstAttemptCompletionRatePercent").value(100.0));
    }

    @Test
    void summary_forContributor_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry/tasks/summary").with(authentication(authFor("contributor"))))
                .andExpect(status().isForbidden());
    }
}

