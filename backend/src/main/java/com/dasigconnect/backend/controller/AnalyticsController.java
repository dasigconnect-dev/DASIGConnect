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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.dasigconnect.backend.model.dto.analytics.AnalyticsReportDto;
import com.dasigconnect.backend.model.dto.analytics.AnalyticsSummaryDto;
import com.dasigconnect.backend.model.dto.analytics.ContentInsightsDto;
import com.dasigconnect.backend.model.dto.analytics.PageAudienceInsightsDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.FacebookInsightsSyncService;
import com.dasigconnect.backend.service.FacebookPageAudienceService;
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
    private final FacebookInsightsSyncService facebookInsightsSyncService;
    private final FacebookPageAudienceService pageAudienceService;

    public AnalyticsController(
            MetricsAggregatorService metricsAggregatorService,
            FacebookInsightsSyncService facebookInsightsSyncService,
            FacebookPageAudienceService pageAudienceService) {
        this.metricsAggregatorService = metricsAggregatorService;
        this.facebookInsightsSyncService = facebookInsightsSyncService;
        this.pageAudienceService = pageAudienceService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> summary(
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(metricsAggregatorService.summary(range, institutionId, user));
    }

    @GetMapping(value = "/export/{metric}", produces = "text/csv")
    public ResponseEntity<String> export(
            @PathVariable String metric,
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        CsvExport export = metricsAggregatorService.export(metric, range, institutionId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .body(export.content());
    }

    @GetMapping("/report/{metric}")
    public ResponseEntity<AnalyticsReportDto> report(
            @PathVariable String metric,
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(metricsAggregatorService.report(metric, range, institutionId, user));
    }

    @GetMapping("/content-insights")
    public ResponseEntity<ContentInsightsDto> contentInsights(
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(metricsAggregatorService.contentInsights(range, institutionId, user));
    }

    @PostMapping("/facebook-insights/sync")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<FacebookInsightsSyncResponseDto> syncFacebookInsights() {
        FacebookInsightsSyncService.SyncResult result = facebookInsightsSyncService.syncDueMetrics();
        return ResponseEntity.ok(new FacebookInsightsSyncResponseDto(
                result.synced(), result.due(), result.failed(), result.reason()));
    }

    /** UC-4.8 Phase 5b: page-level audience (followers + best-effort demographics). */
    @GetMapping("/audience-insights")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<PageAudienceInsightsDto> audienceInsights(
            @RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(pageAudienceService.getAudienceInsights(range));
    }

    @PostMapping("/audience-insights/sync")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<AudienceSyncResponseDto> syncAudienceInsights() {
        boolean stored = pageAudienceService.syncAudience();
        return ResponseEntity.ok(new AudienceSyncResponseDto(stored));
    }

    public record FacebookInsightsSyncResponseDto(int syncedPosts, int duePosts, int failedPosts, String reason) {}

    public record AudienceSyncResponseDto(boolean stored) {}
}
