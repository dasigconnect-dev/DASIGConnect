package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dasigconnect.backend.model.entity.FacebookPageToken;

public interface FacebookPageTokenRepository extends JpaRepository<FacebookPageToken, UUID> {

    Optional<FacebookPageToken> findByPageIdAndIsActiveTrue(String pageId);

    /** Regardless of active state — {@code page_id} is unique, so this is at most one row. */
    Optional<FacebookPageToken> findByPageId(String pageId);

    /** Every active row for a page other than {@code pageId} — used to retire stale rows when FACEBOOK_PAGE_ID changes. */
    List<FacebookPageToken> findByIsActiveTrueAndPageIdNot(String pageId);

    /**
     * The page the system actually publishes to right now. At most one row is
     * ever active — enforced by {@code deactivateOtherActiveTokens} whenever a
     * new page is connected — so "first" is really "the only one".
     */
    Optional<FacebookPageToken> findFirstByIsActiveTrue();
}
