package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.AccountLockout;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AccountLockoutRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLockoutService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final AccountLockoutRepository lockoutRepository;

    public AccountLockoutService(AccountLockoutRepository lockoutRepository) {
        this.lockoutRepository = lockoutRepository;
    }

    public boolean isLocked(UUID userId) {
        return lockoutRepository.findById(userId)
                .filter(l -> l.getLockedUntil() != null && l.getLockedUntil().isAfter(Instant.now()))
                .isPresent();
    }

    /**
     * Records one failed sign-in. Returns the running count of consecutive
     * failures, and whether this attempt is the one that tripped the lock
     * (true only on the transition, not on subsequent attempts while locked),
     * so the caller can write a single {@code ACCOUNT_LOCKED} audit entry.
     */
    @Transactional
    public FailedAttemptResult recordFailedAttempt(User user) {
        AccountLockout lockout = lockoutRepository.findById(user.getId())
                .orElseGet(() -> {
                    AccountLockout fresh = new AccountLockout();
                    fresh.setUser(user);
                    return fresh;
                });
        boolean wasLocked = lockout.getLockedUntil() != null && lockout.getLockedUntil().isAfter(Instant.now());
        lockout.setFailedAttempts(lockout.getFailedAttempts() + 1);
        lockout.setLastAttemptAt(Instant.now());
        boolean nowLocked = lockout.getFailedAttempts() >= MAX_ATTEMPTS;
        if (nowLocked) {
            lockout.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
        }
        lockoutRepository.save(lockout);
        return new FailedAttemptResult(lockout.getFailedAttempts(), nowLocked && !wasLocked);
    }

    /** @param justLocked true only on the attempt that first trips the lock. */
    public record FailedAttemptResult(int failedAttempts, boolean justLocked) {}

    @Transactional
    public void clearLockout(User user) {
        lockoutRepository.findById(user.getId()).ifPresent(lockout -> {
            lockout.setFailedAttempts(0);
            lockout.setLockedUntil(null);
            lockoutRepository.save(lockout);
        });
    }
}
