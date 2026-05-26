package com.dasigconnect.backend.controller;

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

    public AnalyticsController(MetricsAggregatorService metricsAggregatorService) {
        this.metricsAggregatorService = metricsAggregatorService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> summary(
            @RequestParam(defaultValue = "30d") String range,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(metricsAggregatorService.summary(range, user));
    }

    @GetMapping(value = "/export/{metric}", produces = "text/csv")
    public ResponseEntity<String> export(
            @PathVariable String metric,
            @RequestParam(defaultValue = "30d") String range,
            @AuthenticationPrincipal JwtUserDetails user) {
        CsvExport export = metricsAggregatorService.export(metric, range, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .body(export.content());
    }
}
