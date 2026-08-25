package com.dasigconnect.backend.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dasigconnect.backend.config.SecurityConfig;
import com.dasigconnect.backend.model.dto.calendar.CalendarEventDto;
import com.dasigconnect.backend.model.dto.submission.RescheduleRequestDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.service.CalendarService;
import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.SubmissionService;
import com.dasigconnect.backend.service.TenantScopeService;

@WebMvcTest(CalendarController.class)
@Import(SecurityConfig.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarService calendarService;

    @MockitoBean
    private SubmissionService submissionService;

    @MockitoBean
    private JWTService jwtService;

    @MockitoBean
    private TenantScopeService tenantScopeService;

    @Test
    @DisplayName("GET /api/v1/calendar without auth returns 403 Forbidden")
    void getCalendar_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/calendar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/calendar with authenticated user returns 200 OK")
    @WithMockUser(username = "contributor@cit.edu", roles = {"CONTRIBUTOR"})
    void getCalendar_authenticated_returns200() throws Exception {
        CalendarEventDto event = new CalendarEventDto();
        when(calendarService.getCalendarEvents(any())).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        verify(calendarService).getCalendarEvents(any());
    }

    @Test
    @DisplayName("PATCH /api/v1/submissions/{id}/reschedule as Contributor returns 403 Forbidden")
    @WithMockUser(username = "contributor@cit.edu", roles = {"CONTRIBUTOR"})
    void reschedule_asContributor_returnsForbidden() throws Exception {
        UUID submissionId = UUID.randomUUID();
        String json = """
            {
                "scheduledAt": "2026-08-26T14:00:00Z",
                "overrideReason": "Reorganizing slot"
            }
        """;

        mockMvc.perform(patch("/api/v1/submissions/" + submissionId + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/submissions/{id}/reschedule as Administrator returns 200 OK")
    @WithMockUser(username = "admin@dasig.org", roles = {"ADMINISTRATOR"})
    void reschedule_asAdministrator_returns200() throws Exception {
        UUID submissionId = UUID.randomUUID();
        
        com.dasigconnect.backend.model.entity.Institution inst = new com.dasigconnect.backend.model.entity.Institution();
        inst.setId(UUID.randomUUID());
        inst.setName("CIT-U");
        
        com.dasigconnect.backend.model.entity.User user = new com.dasigconnect.backend.model.entity.User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@dasig.org");
        
        com.dasigconnect.backend.model.entity.Submission submission = new com.dasigconnect.backend.model.entity.Submission();
        submission.setId(submissionId);
        submission.setInstitution(inst);
        submission.setContributor(user);
        submission.setStatus(com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled);
        submission.setScheduledAt(Instant.parse("2026-08-26T14:00:00Z"));

        SubmissionResponseDto response = SubmissionResponseDto.from(submission, List.of());

        when(submissionService.reschedule(eq(submissionId), any(RescheduleRequestDto.class), any()))
                .thenReturn(response);

        String json = """
            {
                "scheduledAt": "2026-08-26T14:00:00Z",
                "overrideReason": "Reorganizing slot"
            }
        """;

        mockMvc.perform(patch("/api/v1/submissions/" + submissionId + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(submissionService).reschedule(eq(submissionId), any(RescheduleRequestDto.class), any());
    }
}
