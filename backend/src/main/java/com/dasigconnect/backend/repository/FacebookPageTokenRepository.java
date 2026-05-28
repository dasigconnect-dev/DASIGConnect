package com.dasigconnect.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.FacebookPageToken;

public interface FacebookPageTokenRepository extends JpaRepository<FacebookPageToken, UUID> {

    Optional<FacebookPageToken> findByPageIdAndIsActiveTrue(String pageId);
}
