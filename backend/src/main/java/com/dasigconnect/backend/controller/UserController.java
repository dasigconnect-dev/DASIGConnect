package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.user.UpdateAccountSettingsRequestDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dasigconnect.backend.model.dto.common.ApiResponse;
import com.dasigconnect.backend.model.dto.user.ChangeUserRoleRequestDto;
import com.dasigconnect.backend.model.dto.user.ReassignContributorRequest;
import com.dasigconnect.backend.model.dto.user.AdminTransferResponseDto;
import com.dasigconnect.backend.model.dto.user.UpdateUserStatusRequestDto;
import com.dasigconnect.backend.model.dto.user.UserDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.UserService;

import jakarta.validation.Valid;

/**
 * User profile and management endpoints. Base path: /api/v1
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/v1/me Returns the profile of the currently authenticated user.
     * Used by the frontend to get reliable identity data (role, name,
     * institution) rather than parsing it from the JWT payload.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> me(@AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(user)));
    }

    @PatchMapping("/me/settings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> updateMySettings(
            @RequestBody @Valid UpdateAccountSettingsRequestDto request,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateSettings(user, request)));
    }

    /**
     * GET /api/v1/users?institutionId={uuid} Lists all users for a given
     * institution. Admins and moderators may query institution users.
     */
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<ApiResponse<List<UserDto>>> listUsers(
            @RequestParam UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.listByInstitution(institutionId, user)));
    }

    @GetMapping("/users/admins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDto>>> listAdmins(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.listAdmins(user)));
    }

    @GetMapping("/users/moderators")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDto>>> listModerators(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.listModerators(user)));
    }

    /**
     * GET /api/v1/users/network Lists all contributor and moderator accounts
     * across every institution. Admin-only.
     */
    @GetMapping("/users/network")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDto>>> listNetworkUsers(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.listNetworkUsers(user)));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<ApiResponse<UserDto>> getUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(id, user)));
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateUserStatusRequestDto request,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateStatus(id, request.accountState(), user)));
    }

    /**
     * PATCH /api/v1/users/{id}/role Promotes or demotes an account between
     * contributor, moderator, and admin. Admin-authenticated; the service layer
     * refines this (peer admin for contributor/moderator, Admin Owner for
     * anything touching an admin account).
     */
    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> changeRole(
            @PathVariable UUID id,
            @RequestBody @Valid ChangeUserRoleRequestDto request,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.changeRole(id, request.role(), request.institutionId(), user)));
    }

    /**
     * PATCH /api/v1/users/{id}/institution Reassigns a contributor account to a
     * different institution (Moderator-only, A4). Moderators cannot be reassigned
     * through this endpoint. Historical submissions retain their original
     * institution attribution.
     */
    @PatchMapping("/users/{id}/institution")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> reassignInstitution(
            @PathVariable UUID id,
            @RequestBody @Valid ReassignContributorRequest request,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.reassignContributor(id, request.getTargetInstitutionId(), user)));
    }

    @PutMapping(value = "/users/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDto>> updateAvatar(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateAvatar(id, file, user)));
    }

    @GetMapping("/users/{id}/avatar")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getAvatar(@PathVariable UUID id) {
        UserService.UserAvatar avatar = userService.getAvatar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(avatar.data());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> removeUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        String action = userService.removeUser(id, user);
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("action", action)));
    }

    /**
     * POST /api/v1/users/{id}/erase Anonymises an account's personal data
     * ("right to be forgotten"). Admin Owner only (enforced in the service).
     * The account must already be deactivated or cancelled.
     */
    @PostMapping("/users/{id}/erase")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserService.ErasureResult>> erasePersonalData(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.erasePersonalData(id, user)));
    }

    @PostMapping("/users/{id}/admin-transfer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminTransferResponseDto>> requestAdminTransfer(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.requestAdminTransfer(id, user)));
    }

    @PostMapping("/users/admin-transfer/confirm")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<UserDto>> confirmAdminTransfer(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.confirmAdminTransfer(user)));
    }

    /**
     * GET /api/v1/users/counts?institutionId={uuid} Returns contributor and
     * validator counts for an institution. Used by dashboard summary tiles.
     */
    @GetMapping("/users/counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> userCounts(
            @RequestParam UUID institutionId,
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(userService.countByRole(institutionId, user)));
    }
}
