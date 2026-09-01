package com.dasigconnect.backend.controller;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.model.dto.analytics.AnalyticsReportDto;
import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.analytics.AnalyticsSummaryDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.MetricsAggregatorService;
import com.dasigconnect.backend.service.MetricsAggregatorService.CsvExport;

/**
 * UC-2.4 Analytics Dashboard.
 * Base path: /api/v1/analytics
 */
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("isAuthenticated()")
public class AnalyticsController {

    private final MetricsAggregatorService metricsAggregatorService;
    private final com.dasigconnect.backend.service.AuditLogService auditLogService;

    public AnalyticsController(MetricsAggregatorService metricsAggregatorService,
            com.dasigconnect.backend.service.AuditLogService auditLogService) {
        this.metricsAggregatorService = metricsAggregatorService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsSummaryDto>> summary(
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(metricsAggregatorService.summary(range, institutionId, category, user)));
    }

    @GetMapping(value = "/export/{metric}", produces = "text/csv")
    public ResponseEntity<String> export(
            @PathVariable String metric,
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal JwtUserDetails user) {
        CsvExport export = metricsAggregatorService.export(metric, range, institutionId, category, user);
        auditLogService.recordByActorId(user != null ? user.userId() : null,
                "ANALYTICS_EXPORTED", null, null, institutionId,
                java.util.Map.of("metric", metric, "range", range,
                        "institutionScope", institutionId != null ? institutionId.toString() : "all"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .body(export.content());
    }

    @GetMapping("/report/{metric}")
    public ResponseEntity<ApiResponse<AnalyticsReportDto>> report(
            @PathVariable String metric,
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(metricsAggregatorService.report(metric, range, institutionId, category, user)));
    }
}
