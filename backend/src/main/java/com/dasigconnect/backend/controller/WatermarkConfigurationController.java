package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.WatermarkConfigurationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings/watermark")
@PreAuthorize("hasAnyRole('ADMINISTRATOR','SUPER_ADMINISTRATOR')")
public class WatermarkConfigurationController {

    private final WatermarkConfigurationService service;

    public WatermarkConfigurationController(WatermarkConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<WatermarkConfigurationDto> get(
            @RequestParam(required = false) UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails actor
    ) {
        return ResponseEntity.ok(service.get(institutionId, actor));
    }

    @PutMapping
    public ResponseEntity<WatermarkConfigurationDto> save(
            @RequestBody @Valid WatermarkConfigurationRequestDto request,
            @AuthenticationPrincipal JwtUserDetails actor
    ) {
        return ResponseEntity.ok(service.save(request, actor));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteOverride(
            @RequestParam UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails actor
    ) {
        service.deleteOverride(institutionId, actor);
        return ResponseEntity.noContent().build();
    }
}
