package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.settings.PageSettingsDto;
import com.dasigconnect.backend.model.dto.settings.UpdatePageSettingsRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.PageSettingsService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings/page")
@PreAuthorize("hasAnyRole('ADMINISTRATOR','SUPER_ADMINISTRATOR')")
public class PageSettingsController {
    private final PageSettingsService service;
    public PageSettingsController(PageSettingsService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<PageSettingsDto>> get(@RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success(service.get(institutionId, actor)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<PageSettingsDto>> update(@RequestParam(required = false) UUID institutionId,
            @RequestBody @Valid UpdatePageSettingsRequestDto request,
            @AuthenticationPrincipal JwtUserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success(service.update(institutionId, request, actor)));
    }
}
