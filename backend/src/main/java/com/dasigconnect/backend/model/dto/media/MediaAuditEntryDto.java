package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.AuditLog;
import com.dasigconnect.backend.model.entity.User;
import java.time.Instant;

/**
 * UC-4.11: one entry in a media asset's provenance trail. {@code actorName}/{@code actorEmail}
 * are null for system actions (e.g. retention purge). {@code metadata} is the raw audit JSON.
 */
public record MediaAuditEntryDto(
        String action,
        String actorName,
        String actorEmail,
        Instant createdAt,
        String metadata) {

    public static MediaAuditEntryDto from(AuditLog log) {
        User actor = log.getActor();
        String actorName = null;
        String actorEmail = null;
        if (actor != null) {
            actorEmail = actor.getEmail();
            String first = actor.getFirstName() == null ? "" : actor.getFirstName().trim();
            String last = actor.getLastName() == null ? "" : actor.getLastName().trim();
            String full = (first + " " + last).trim();
            actorName = full.isEmpty() ? null : full;
        }
        return new MediaAuditEntryDto(log.getAction(), actorName, actorEmail, log.getCreatedAt(), log.getMetadata());
    }
}
