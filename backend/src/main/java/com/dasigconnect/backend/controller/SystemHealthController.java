package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.exception.OAuthInitResponseDto;
import com.dasigconnect.backend.model.dto.exception.TokenStatusDto;
import com.dasigconnect.backend.model.dto.systemhealth.BackgroundJobHealthDto;
import com.dasigconnect.backend.model.dto.systemhealth.ExternalServiceHealthDto;
import com.dasigconnect.backend.model.dto.systemhealth.OperationalMetricDto;
import com.dasigconnect.backend.model.dto.systemhealth.StorageMetricDto;
import com.dasigconnect.backend.model.dto.systemhealth.SystemHealthSummaryDto;
import com.dasigconnect.backend.service.SystemHealthService;
import com.dasigconnect.backend.service.TokenManagementService;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system-health")
@PreAuthorize("hasAnyRole('SUPER_ADMINISTRATOR', 'ADMINISTRATOR')")
public class SystemHealthController {

    private final SystemHealthService systemHealthService;
    private final TokenManagementService tokenManagementService;

    public SystemHealthController(
            SystemHealthService systemHealthService,
            TokenManagementService tokenManagementService) {
        this.systemHealthService = systemHealthService;
        this.tokenManagementService = tokenManagementService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SystemHealthSummaryDto>> summary() {
        return ResponseEntity.ok(ApiResponse.success(systemHealthService.summary()));
    }

    @GetMapping("/storage")
    public ResponseEntity<ApiResponse<List<StorageMetricDto>>> storage() {
        return ResponseEntity.ok(ApiResponse.success(systemHealthService.storage()));
    }

    @GetMapping("/external-services")
    public ResponseEntity<ApiResponse<List<ExternalServiceHealthDto>>> externalServices() {
        return ResponseEntity.ok(ApiResponse.success(systemHealthService.externalServices()));
    }

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<BackgroundJobHealthDto>>> jobs() {
        return ResponseEntity.ok(ApiResponse.success(systemHealthService.backgroundJobs()));
    }

    @GetMapping("/operational-metrics")
    public ResponseEntity<ApiResponse<List<OperationalMetricDto>>> operationalMetrics() {
        return ResponseEntity.ok(ApiResponse.success(systemHealthService.operationalMetrics()));
    }

    @GetMapping("/tokens")
    public ResponseEntity<ApiResponse<List<TokenStatusDto>>> tokens() {
        return ResponseEntity.ok(ApiResponse.success(tokenManagementService.getAllTokenStatuses()));
    }

    @GetMapping("/tokens/{tokenId}/oauth-init")
    public ResponseEntity<ApiResponse<OAuthInitResponseDto>> oauthInit(
            @PathVariable java.util.UUID tokenId,
            @AuthenticationPrincipal JwtUserDetails admin) {
        return ResponseEntity.ok(ApiResponse.success(tokenManagementService.initOAuth(tokenId, admin)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportSnapshot() {
        String filename = "DASIGConnect_System_Health_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(systemHealthService.exportSnapshotCsv());
    }
}
