package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.InvitationToken;
import com.dasigconnect.backend.model.entity.UserRole;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, UUID> {
    Optional<InvitationToken> findByTokenHash(String tokenHash);

    /** Removes every invitation token for an address, regardless of used/expired state. */
    @Modifying
    @Query(value = "DELETE FROM invitation_tokens WHERE lower(recipient_email) = lower(:email)", nativeQuery = true)
    int deleteByRecipientEmailIgnoreCase(@Param("email") String email);
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

    /** Pending contributor/moderator invitations across every institution, for the network-wide User Management page. */
    @Query("""
            select token
            from InvitationToken token
            left join fetch token.institution
            where token.assignedRole in :assignedRoles
              and token.usedAt is null
              and token.expiresAt > :now
            order by token.createdAt desc
            """)
    List<InvitationToken> findPendingRoleInvitationsAcrossInstitutions(
            @Param("assignedRoles") Collection<UserRole> assignedRoles,
            @Param("now") Instant now);
}
