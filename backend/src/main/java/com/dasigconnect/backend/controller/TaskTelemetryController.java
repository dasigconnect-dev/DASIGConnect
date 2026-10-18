package com.dasigconnect.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.TaskTelemetryService;
import com.dasigconnect.backend.service.TaskTelemetryService.NextActionDto;
import com.dasigconnect.backend.service.TaskTelemetryService.RoleTaskSummaryDto;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskCompleteRequest;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskEventRequest;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskStartRequest;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskTelemetryDto;

/**
 * REST endpoints for recording and summarizing first-attempt task completion,
 * time-on-task, and next-action guidance across roles (Priority #2 — RP-02).
 */
@RestController
@RequestMapping("/api/v1")
public class TaskTelemetryController {

    private final TaskTelemetryService taskTelemetryService;

    public TaskTelemetryController(TaskTelemetryService taskTelemetryService) {
        this.taskTelemetryService = taskTelemetryService;
    }

    @GetMapping("/me/next-action")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NextActionDto>> nextAction(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(taskTelemetryService.getNextAction(user)));
    }

    @PostMapping("/task-events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TaskTelemetryDto>> recordTaskEvent(
            @AuthenticationPrincipal JwtUserDetails user,
            @RequestBody TaskEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskTelemetryService.recordTaskEvent(user, request)));
    }

    @PostMapping("/telemetry/tasks/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TaskTelemetryDto>> startTask(
            @AuthenticationPrincipal JwtUserDetails user,
            @RequestBody TaskStartRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskTelemetryService.startTask(user, request)));
    }

    @PostMapping("/telemetry/tasks/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TaskTelemetryDto>> completeTask(
            @AuthenticationPrincipal JwtUserDetails user,
            @RequestBody TaskCompleteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskTelemetryService.completeTask(user, request)));
    }

    @GetMapping("/telemetry/tasks/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<ApiResponse<List<RoleTaskSummaryDto>>> summary() {
        return ResponseEntity.ok(ApiResponse.success(taskTelemetryService.getSummary()));
    }
}

