package com.dasigconnect.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import com.dasigconnect.backend.model.dto.calendar.CalendarEventDto;
import com.dasigconnect.backend.model.dto.submission.RescheduleRequestDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.CalendarService;
import com.dasigconnect.backend.service.SubmissionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class CalendarController {

    private final CalendarService calendarService;
    private final SubmissionService submissionService;

    public CalendarController(CalendarService calendarService, SubmissionService submissionService) {
        this.calendarService = calendarService;
        this.submissionService = submissionService;
    }

    @GetMapping("/calendar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CalendarEventDto>> getCalendar(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(calendarService.getCalendarEvents(user));
    }

    @PatchMapping("/submissions/{id}/reschedule")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<SubmissionResponseDto> reschedule(
            @PathVariable UUID id,
            @RequestBody @Valid RescheduleRequestDto dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(submissionService.reschedule(id, dto, user));
    }
}
