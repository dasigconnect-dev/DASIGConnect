package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.auth.LoginResponseDto;
import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.invitation.AcceptInvitationRequestDto;
import com.dasigconnect.backend.model.dto.invitation.CreateInvitationRequestDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationResponseDto;
import com.dasigconnect.backend.model.dto.invitation.InvitationValidateResponseDto;
import com.dasigconnect.backend.model.dto.invitation.PendingInvitationDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @PostMapping
    public ResponseEntity<ApiResponse<InvitationResponseDto>> create(
            @RequestBody @Valid CreateInvitationRequestDto dto,
            Authentication authentication) {
        JwtUserDetails inviter = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails principal
                ? principal
                : null;
        return ResponseEntity.status(201).body(ApiResponse.success(invitationService.createInvitation(dto, inviter)));
    }

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<InvitationValidateResponseDto>> validate(
            @RequestParam String token) {
        return ResponseEntity.ok(ApiResponse.success(invitationService.validateToken(token)));
    }

    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<LoginResponseDto>> accept(
            @RequestBody @Valid AcceptInvitationRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success(invitationService.acceptInvitation(dto)));
    }

    @PostMapping("/resend-expired")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendExpired(
            @RequestBody Map<String, String> request) {
        String token = request != null ? request.get("token") : null;
        String email = request != null ? request.get("email") : null;
        invitationService.resendExpiredToken(token, email);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "A new invitation link has been sent to your email.")));
    }

    /**
     * POST /api/v1/invitations/{id}/resend
     * Resends the invitation email with a fresh token.
     * Used when the original delivery failed (pending_email_undelivered).
     */
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @PostMapping("/{id}/resend")
    public ResponseEntity<ApiResponse<InvitationResponseDto>> resend(
            @PathVariable UUID id,
            Authentication authentication) {
        JwtUserDetails requester = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails p ? p : null;
        return ResponseEntity.ok(ApiResponse.success(invitationService.resend(id, requester)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID id,
            Authentication authentication) {
        JwtUserDetails requester = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails p ? p : null;
        invitationService.cancel(id, requester);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * DELETE /api/v1/invitations/by-user/{userId}
     * Cancels a pending account by user id — reliable even when the invitation
     * token has expired, unlike DELETE /{id} which needs a live token.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/by-user/{userId}")
    public ResponseEntity<Void> cancelByUser(
            @PathVariable UUID userId,
            Authentication authentication) {
        JwtUserDetails requester = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails p ? p : null;
        invitationService.cancelPendingUserInvitation(userId, requester);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PendingInvitationDto>>> pending(
            @RequestParam UUID institutionId,
            Authentication authentication) {
        JwtUserDetails requester = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails p ? p : null;
        return ResponseEntity.ok(ApiResponse.success(invitationService.listPending(institutionId, requester)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending/admins")
    public ResponseEntity<ApiResponse<List<PendingInvitationDto>>> pendingAdmins(
            Authentication authentication) {
        JwtUserDetails requester = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails p ? p : null;
        return ResponseEntity.ok(ApiResponse.success(invitationService.listPendingAdmins(requester)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending/network")
    public ResponseEntity<ApiResponse<List<PendingInvitationDto>>> pendingNetwork(
            Authentication authentication) {
        JwtUserDetails requester = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails p ? p : null;
        return ResponseEntity.ok(ApiResponse.success(invitationService.listPendingNetwork(requester)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @GetMapping("/pending/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> pendingCount(
            @RequestParam UUID institutionId,
            Authentication authentication) {
        JwtUserDetails requester = authentication != null && authentication.getPrincipal() instanceof JwtUserDetails p ? p : null;
        return ResponseEntity.ok(ApiResponse.success(invitationService.countPending(institutionId, requester)));
    }
}
