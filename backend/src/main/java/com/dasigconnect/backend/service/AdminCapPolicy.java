package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.entity.InvitationToken;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.model.entity.UserStatus;
import com.dasigconnect.backend.repository.InvitationTokenRepository;
import com.dasigconnect.backend.repository.UserRepository;

/**
 * Single source of truth for the Administrator headcount cap (UC-1.1 —
 * "Administrator Cap Enforcement").
 *
 * <p>The invariant enforced everywhere Administrator-tier access could be
 * granted is:
 *
 * <pre>(confirmed admins) + (pending admin invites) + (pending promotions) &le; app.admins.max</pre>
 *
 * <p>The Admin Owner counts toward this total and has no override. Owner
 * transfer is exempt — it moves the {@code admin_owner} flag between two
 * already-counted accounts and never changes the total.
 *
 * <p>Slots release automatically: an invite past its 72&nbsp;h TTL and an
 * expired pending promotion both drop out of their respective queries, so no
 * cleanup job is needed for the cap to stay correct.
 */
@Component
public class AdminCapPolicy {

    private final UserRepository userRepository;
    private final InvitationTokenRepository invitationTokenRepository;

    @Value("${app.admins.max:3}")
    private long maxAdmins;

    public AdminCapPolicy(UserRepository userRepository, InvitationTokenRepository invitationTokenRepository) {
        this.userRepository = userRepository;
        this.invitationTokenRepository = invitationTokenRepository;
    }

    public long maxAdmins() {
        return maxAdmins;
    }

    /**
     * Administrator slots currently spoken for.
     *
     * @param excludeInviteEmail       a pending admin-invite recipient to leave out of the count
     *                                 (the invite being accepted / resend-replaced), or {@code null}
     * @param excludePromotionUserId   a user whose live pending promotion to leave out of the count
     *                                 (the promotion being confirmed / re-requested), or {@code null}
     */
    public long slotsInUse(String excludeInviteEmail, UUID excludePromotionUserId) {
        Instant now = Instant.now();

        long activeAdmins = userRepository.countByRoleAndAccountState(UserRole.admin, UserStatus.active);

        long pendingInvites = invitationTokenRepository
                .findPendingNetworkRoleInvitations(UserRole.admin, now)
                .stream()
                .map(InvitationToken::getRecipientEmail)
                .filter(email -> excludeInviteEmail == null || !email.equalsIgnoreCase(excludeInviteEmail))
                .distinct()
                .count();

        long pendingPromotions = userRepository.countLivePendingAdminPromotions(now);
        if (excludePromotionUserId != null
                && userRepository.hasLivePendingAdminPromotion(excludePromotionUserId, now)) {
            pendingPromotions--;
        }

        return activeAdmins + pendingInvites + pendingPromotions;
    }

    /**
     * Throws {@code 409 CONFLICT} with the uniform cap message when no
     * Administrator slot is free.
     */
    public void assertHasFreeSlot(String excludeInviteEmail, UUID excludePromotionUserId) {
        if (slotsInUse(excludeInviteEmail, excludePromotionUserId) >= maxAdmins) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Administrator limit of " + maxAdmins
                            + " reached — cannot invite, promote, or reactivate until a slot is freed.");
        }
    }
}
