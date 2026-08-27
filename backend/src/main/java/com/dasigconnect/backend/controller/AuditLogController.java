package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.audit.AuditEntityType;
import com.dasigconnect.backend.model.dto.audit.AuditLogCategory;
import com.dasigconnect.backend.model.dto.audit.AuditLogDto;
import com.dasigconnect.backend.model.dto.audit.AuditLogFilterCriteria;
import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.service.AuditLogService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-log")
@PreAuthorize("hasAnyRole('SUPER_ADMINISTRATOR', 'ADMINISTRATOR')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getAuditLogs(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String actorQuery,
            @RequestParam(required = false) AuditLogCategory category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Instant start = parseInstant(startDate);
        Instant end = parseInstant(endDate);

        AuditLogFilterCriteria criteria = new AuditLogFilterCriteria(
                start, end, actorId, actorQuery, category, action, entityType, resourceId, search
        );

        int clampedSize = Math.min(Math.max(size, 1), 100);
        int clampedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLogDto> results = auditLogService.searchAuditLogs(criteria, pageable);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getMetadataOptions()));
    }

    @GetMapping(value = "/export-csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String actorQuery,
            @RequestParam(required = false) AuditLogCategory category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) String search
    ) {
        Instant start = parseInstant(startDate);
        Instant end = parseInstant(endDate);

        AuditLogFilterCriteria criteria = new AuditLogFilterCriteria(
                start, end, actorId, actorQuery, category, action, entityType, resourceId, search
        );

        String csvData = auditLogService.exportAuditLogsCsv(criteria);
        String filename = "DASIGConnect_AuditLog_" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(csvData);
    }

    private Instant parseInstant(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Instant.parse(str.trim());
        } catch (DateTimeParseException e) {
            try {
                // If provided as YYYY-MM-DD, parse as start/end of day UTC
                return LocalDate.parse(str.trim()).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
