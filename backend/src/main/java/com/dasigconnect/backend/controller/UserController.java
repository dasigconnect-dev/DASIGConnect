package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.model.dto.user.UpdateUserStatusRequestDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * User profile and management endpoints.
 * Base path: /api/v1
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/v1/me
     * Returns the profile of the currently authenticated user.
     * Used by the frontend to get reliable identity data (role, name, institution)
     * rather than parsing it from the JWT payload.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(userService.getProfile(user));
    }

    /**
     * GET /api/v1/users?institutionId={uuid}
     * Lists all users for a given institution.
     * - ADMINISTRATOR: any institution
     * - VALIDATOR: own institution only
     */
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'VALIDATOR')")
    public ResponseEntity<List<UserDto>> listUsers(
            @RequestParam UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(userService.listByInstitution(institutionId, user));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'VALIDATOR')")
    public ResponseEntity<UserDto> getUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(userService.getById(id, user));
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'VALIDATOR')")
    public ResponseEntity<UserDto> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateUserStatusRequestDto request,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(userService.updateStatus(id, request.accountState(), user));
    }

    /**
     * GET /api/v1/users/counts?institutionId={uuid}
     * Returns contributor and validator counts for an institution.
     * Used by dashboard summary tiles.
     */
    @GetMapping("/users/counts")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'VALIDATOR')")
    public ResponseEntity<Map<String, Long>> userCounts(
            @RequestParam UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(userService.countByRole(institutionId, user));
    }
}
