package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.auth.LoginResponseDto;
import com.dasigconnect.backend.model.dto.invitation.AcceptInvitationRequestDto;
import com.dasigconnect.backend.model.dto.invitation.CreateInvitationRequestDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationResponseDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationValidateResponseDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRATOR','VALIDATOR')")
    @PostMapping
    public ResponseEntity<InvitationResponseDto> create(
            @RequestBody @Valid CreateInvitationRequestDto dto,
            Authentication authentication) {
        JwtUserDetails inviter = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails principal
                ? principal
                : null;
        return ResponseEntity.status(201).body(invitationService.createInvitation(dto, inviter));
    }

    @GetMapping("/validate")
    public ResponseEntity<InvitationValidateResponseDto> validate(
            @RequestParam String token) {
        return ResponseEntity.ok(invitationService.validateToken(token));
    }

    @PostMapping("/accept")
    public ResponseEntity<LoginResponseDto> accept(
            @RequestBody @Valid AcceptInvitationRequestDto dto) {
        return ResponseEntity.ok(invitationService.acceptInvitation(dto));
    }

    /**
     * POST /api/v1/invitations/{id}/resend
     * Resends the invitation email with a fresh token.
     * Used when the original delivery failed (pending_email_undelivered).
     */
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','VALIDATOR')")
    @PostMapping("/{id}/resend")
    public ResponseEntity<InvitationResponseDto> resend(
            @PathVariable UUID id,
            Authentication authentication) {
        JwtUserDetails requester = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails p ? p : null;
        return ResponseEntity.ok(invitationService.resend(id, requester));
    }
}
