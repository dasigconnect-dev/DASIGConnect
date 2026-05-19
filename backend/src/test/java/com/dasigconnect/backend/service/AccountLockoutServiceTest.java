package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.AccountLockout;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AccountLockoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

    @Mock AccountLockoutRepository lockoutRepository;
    @InjectMocks AccountLockoutService accountLockoutService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
    }

    @Test
    void isLocked_whenNoRecord_returnsFalse() {
        when(lockoutRepository.findById(userId)).thenReturn(Optional.empty());
        assertThat(accountLockoutService.isLocked(userId)).isFalse();
    }

    @Test
    void isLocked_whenLockedUntilInFuture_returnsTrue() {
        AccountLockout lockout = new AccountLockout();
        lockout.setUser(user);
        lockout.setLockedUntil(Instant.now().plusSeconds(300));
        when(lockoutRepository.findById(userId)).thenReturn(Optional.of(lockout));
        assertThat(accountLockoutService.isLocked(userId)).isTrue();
    }

    @Test
    void isLocked_whenLockExpired_returnsFalse() {
        AccountLockout lockout = new AccountLockout();
        lockout.setUser(user);
        lockout.setLockedUntil(Instant.now().minusSeconds(1));
        when(lockoutRepository.findById(userId)).thenReturn(Optional.of(lockout));
        assertThat(accountLockoutService.isLocked(userId)).isFalse();
    }

    @Test
    void recordFailedAttempt_incrementsCounter_whenNoExistingRecord() {
        when(lockoutRepository.findById(userId)).thenReturn(Optional.empty());
        when(lockoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountLockoutService.recordFailedAttempt(user);

        ArgumentCaptor<AccountLockout> captor = ArgumentCaptor.forClass(AccountLockout.class);
        verify(lockoutRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void recordFailedAttempt_setsLockedUntil_afterMaxAttempts() {
        AccountLockout existing = new AccountLockout();
        existing.setUser(user);
        existing.setFailedAttempts(AccountLockoutService.MAX_ATTEMPTS - 1);
        when(lockoutRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(lockoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountLockoutService.recordFailedAttempt(user);

        ArgumentCaptor<AccountLockout> captor = ArgumentCaptor.forClass(AccountLockout.class);
        verify(lockoutRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(AccountLockoutService.MAX_ATTEMPTS);
        assertThat(captor.getValue().getLockedUntil()).isNotNull().isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void clearLockout_resetsCounterAndLockedUntil() {
        AccountLockout existing = new AccountLockout();
        existing.setUser(user);
        existing.setFailedAttempts(3);
        existing.setLockedUntil(Instant.now().plusSeconds(300));
        when(lockoutRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(lockoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountLockoutService.clearLockout(user);

        ArgumentCaptor<AccountLockout> captor = ArgumentCaptor.forClass(AccountLockout.class);
        verify(lockoutRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void clearLockout_whenNoRecord_doesNothing() {
        when(lockoutRepository.findById(userId)).thenReturn(Optional.empty());
        accountLockoutService.clearLockout(user);
        verify(lockoutRepository, never()).save(any());
    }
}
