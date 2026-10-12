package com.dasigconnect.backend.event;

import com.dasigconnect.backend.model.entity.User;

/**
 * An Admin Owner transfer was successfully confirmed.
 * {@code newAdminOwner} has received Admin Owner status (role=admin, isAdminOwner=true).
 * {@code previousAdminOwner} remains an active Administrator (role=admin, isAdminOwner=false).
 */
public record AdminTransferConfirmedEvent(
        User newAdminOwner,
        User previousAdminOwner) {
}

