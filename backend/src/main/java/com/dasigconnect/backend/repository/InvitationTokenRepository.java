package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.InvitationToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, UUID> {
    Optional<InvitationToken> findByTokenHash(String tokenHash);
    List<InvitationToken> findByInstitutionIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID institutionId,
            Instant now);
    List<InvitationToken> findByRecipientEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String recipientEmail,
            Instant now);
    long countByInstitutionIdAndUsedAtIsNullAndExpiresAtAfter(UUID institutionId, Instant now);
}
