package com.dasigconnect.backend.model.dto.user;

import com.dasigconnect.backend.model.entity.User;
import java.time.Instant;
import java.util.UUID;

public class UserDto {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String displayName;
    private String role;
    private String accountState;
    private UUID institutionId;
    private String institutionName;
    private Instant createdAt;
    private boolean notifyInApp;
    private boolean notifyEmail;

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.email = user.getEmail();
        dto.firstName = user.getFirstName();
        dto.lastName = user.getLastName();
        dto.displayName = buildDisplayName(user);
        dto.role = user.getRole().name();
        dto.accountState = user.getAccountState().name();
        dto.institutionId = user.getInstitution() != null ? user.getInstitution().getId() : null;
        dto.institutionName = user.getInstitution() != null ? user.getInstitution().getName() : null;
        dto.createdAt = user.getCreatedAt();
        dto.notifyInApp = user.isNotifyInApp();
        dto.notifyEmail = user.isNotifyEmail();
        return dto;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public String getAccountState() { return accountState; }
    public UUID getInstitutionId() { return institutionId; }
    public String getInstitutionName() { return institutionName; }
    public Instant getCreatedAt() { return createdAt; }
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
