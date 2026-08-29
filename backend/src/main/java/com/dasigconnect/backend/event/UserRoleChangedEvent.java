package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;

/** T18 — an admin changed an account's role (promotion or demotion). */
public record UserRoleChangedEvent(
        User user,
        UserRole fromRole,
        UserRole toRole,
        String actorEmail) {
}
