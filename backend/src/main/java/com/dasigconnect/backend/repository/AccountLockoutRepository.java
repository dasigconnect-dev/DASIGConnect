package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AccountLockout;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLockoutRepository extends JpaRepository<AccountLockout, UUID> {

    void deleteByUserId(UUID userId);
}
