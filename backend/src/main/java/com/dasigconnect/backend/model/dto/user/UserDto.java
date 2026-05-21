package com.dasigconnect.backend.model.dto.user;

import com.dasigconnect.backend.model.entity.User;
import java.time.Instant;
import java.util.UUID;

public class UserDto {

    private UUID id;
    private String email;
    private String role;
    private String accountState;
    private UUID institutionId;
    private String institutionName;
    private Instant createdAt;

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.email = user.getEmail();
        dto.role = user.getRole().name();
        dto.accountState = user.getAccountState().name();
        dto.institutionId = user.getInstitution() != null ? user.getInstitution().getId() : null;
        dto.institutionName = user.getInstitution() != null ? user.getInstitution().getName() : null;
        dto.createdAt = user.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getAccountState() { return accountState; }
    public UUID getInstitutionId() { return institutionId; }
    public String getInstitutionName() { return institutionName; }
    public Instant getCreatedAt() { return createdAt; }
}
