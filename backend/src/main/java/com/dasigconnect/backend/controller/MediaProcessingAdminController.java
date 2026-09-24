package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.systemhealth.MediaProcessingDeadLetterDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.AuditLogService;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system-health/media-processing")
@PreAuthorize("hasRole('ADMIN')")
public class MediaProcessingAdminController {

    private final MediaProcessingQueueService queueService;
    private final AuditLogService auditLogService;

    public MediaProcessingAdminController(
            MediaProcessingQueueService queueService,
            AuditLogService auditLogService) {
        this.queueService = queueService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/dead-letters")
    public ResponseEntity<ApiResponse<List<MediaProcessingDeadLetterDto>>> deadLetters(
            @RequestParam(defaultValue = "25") int limit) {
        List<MediaProcessingDeadLetterDto> jobs = queueService.deadLetters(limit).stream()
                .map(MediaProcessingDeadLetterDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    @PostMapping("/dead-letters/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLetter(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal JwtUserDetails admin) {
        queueService.retryDead(jobId);
        auditLogService.recordByActorId(
                admin != null ? admin.userId() : null,
                "MEDIA_PROCESSING_DEAD_LETTER_RETRIED",
                null,
                null,
                jobId,
                Map.of("jobId", jobId));
        return ResponseEntity.noContent().build();
    }
}
