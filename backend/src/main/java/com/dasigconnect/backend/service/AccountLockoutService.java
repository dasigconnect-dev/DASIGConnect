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

    @Transactional
    public void recordFailedAttempt(User user) {
        AccountLockout lockout = lockoutRepository.findById(user.getId())
                .orElseGet(() -> {
                    AccountLockout fresh = new AccountLockout();
                    fresh.setUser(user);
                    return fresh;
                });
        lockout.setFailedAttempts(lockout.getFailedAttempts() + 1);
        lockout.setLastAttemptAt(Instant.now());
        if (lockout.getFailedAttempts() >= MAX_ATTEMPTS) {
            lockout.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
        }
        lockoutRepository.save(lockout);
    }

    @Transactional
    public void clearLockout(User user) {
        lockoutRepository.findById(user.getId()).ifPresent(lockout -> {
            lockout.setFailedAttempts(0);
            lockout.setLockedUntil(null);
            lockoutRepository.save(lockout);
        });
    }
}
