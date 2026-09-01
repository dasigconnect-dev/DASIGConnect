package com.dasigconnect.backend.model.dto.user;

import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.security.JwtUserDetails;

public class UserDto {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String displayName;
    private String role;
    private String accountState;
    private boolean adminOwner;
    private UUID superAdminTransferRequestedBy;
    private Instant superAdminTransferExpiresAt;
    private UUID institutionId;
    private String institutionName;
    private Instant createdAt;
    private boolean notifyInApp;
    private boolean notifyEmail;
    private boolean hasAvatar;
    private Instant avatarUpdatedAt;
    private Instant purgedAt;
    private UUID invitedByUserId;
    /** Whether the requesting user may delete this row (moderators: only a contributor they invited whose invite was cancelled/expired). */
    private boolean removableByRequester;

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.email = user.getEmail();
        dto.firstName = user.getFirstName();
        dto.lastName = user.getLastName();
        dto.displayName = buildDisplayName(user);
        dto.role = user.getRole().name();
        dto.accountState = user.getAccountState().name();
        dto.adminOwner = user.isAdminOwner();
        dto.superAdminTransferRequestedBy = user.getSuperAdminTransferRequestedBy();
        dto.superAdminTransferExpiresAt = user.getSuperAdminTransferExpiresAt();
        dto.institutionId = user.getInstitution() != null ? user.getInstitution().getId() : null;
        dto.institutionName = user.getInstitution() != null ? user.getInstitution().getName() : null;
        dto.createdAt = user.getCreatedAt();
        dto.notifyInApp = user.isNotifyInApp();
        dto.notifyEmail = user.isNotifyEmail();
        dto.hasAvatar = user.getAvatarData() != null && user.getAvatarData().length > 0;
        dto.avatarUpdatedAt = user.getAvatarUpdatedAt();
        dto.purgedAt = user.getPurgedAt();
        dto.invitedByUserId = user.getInvitedByUserId();
        return dto;
    }

    /** As {@link #from(User)} plus {@code removableByRequester} relative to the caller. */
    public static UserDto from(User user, JwtUserDetails requester) {
        UserDto dto = from(user);
        dto.removableByRequester =
                user.getRole() == UserRole.contributor
                && (user.getAccountState() == UserStatus.cancelled
                        || user.getAccountState() == UserStatus.expired)
                && user.getInvitedByUserId() != null
                && requester != null
                && user.getInvitedByUserId().equals(requester.userId());
        return dto;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public boolean isRemovableByRequester() {
        return removableByRequester;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public String getAccountState() {
        return accountState;
    }

    public boolean isAdminOwner() {
        return adminOwner;
    }

    public UUID getSuperAdminTransferRequestedBy() {
        return superAdminTransferRequestedBy;
    }

    public Instant getSuperAdminTransferExpiresAt() {
        return superAdminTransferExpiresAt;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isHasAvatar() {
        return hasAvatar;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public Instant getPurgedAt() {
        return purgedAt;
    }

    public boolean isNotifyInApp() { return notifyInApp; }
    public boolean isNotifyEmail() { return notifyEmail; }

    private static String buildDisplayName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (first + " " + last).trim();
        return fullName.isBlank() ? null : fullName;
    }
}
