package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailValidateRequest;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.GuardRailService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pre-flight guard rail validation — no submission ID required.
 * Called by the SlotPicker before a draft exists so contributors see
 * scheduling feedback while filling out the form.
 */
@RestController
@RequestMapping("/api/v1/guardrails")
public class GuardRailController {

    private final GuardRailService guardRailService;

    public GuardRailController(GuardRailService guardRailService) {
        this.guardRailService = guardRailService;
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('CONTRIBUTOR','ADMINISTRATOR')")
    public ResponseEntity<GuardRailResult> validate(
            @Valid @RequestBody GuardRailValidateRequest dto,
            @AuthenticationPrincipal JwtUserDetails user) {
        UUID institutionId = "administrator".equals(user.role()) ? dto.getInstitutionId() : user.institutionId();
        if (institutionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select an institution scope before validating a schedule.");
        }
        return ResponseEntity.ok(guardRailService.validate(institutionId, dto.getScheduledAt()));
    }
}
