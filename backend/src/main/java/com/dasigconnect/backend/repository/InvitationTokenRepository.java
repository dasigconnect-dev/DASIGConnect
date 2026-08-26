package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.InvitationToken;
import com.dasigconnect.backend.model.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, UUID> {
    Optional<InvitationToken> findByTokenHash(String tokenHash);
    List<InvitationToken> findByInstitutionIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID institutionId,
            Instant now);
    List<InvitationToken> findByRecipientEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String recipientEmail,
            Instant now);
    long countByInstitutionIdAndUsedAtIsNullAndExpiresAtAfter(UUID institutionId, Instant now);

    void deleteByInstitutionId(UUID institutionId);
    long countByInstitutionIdAndAssignedRoleAndUsedAtIsNullAndExpiresAtAfter(
            UUID institutionId, UserRole assignedRole, Instant now);

    @Query("""
            select token
            from InvitationToken token
            where token.assignedRole = :assignedRole
              and token.institution is null
              and token.usedAt is null
              and token.expiresAt > :now
            order by token.createdAt desc
            """)
    List<InvitationToken> findPendingNetworkRoleInvitations(
            @Param("assignedRole") UserRole assignedRole,
            @Param("now") Instant now);
}
