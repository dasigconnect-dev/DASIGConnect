package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.InvitationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, UUID> {
    Optional<InvitationToken> findByTokenHash(String tokenHash);
}
