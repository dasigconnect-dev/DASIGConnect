package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.FacebookEngagementSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacebookEngagementSnapshotRepository
        extends JpaRepository<FacebookEngagementSnapshot, UUID> {

    Optional<FacebookEngagementSnapshot> findByHistoricalPost_IdAndPageIdAndFetchedAt(
            UUID historicalPostId, String pageId, Instant fetchedAt);

    List<FacebookEngagementSnapshot> findByHistoricalPost_IdAndPageIdOrderByFetchedAtDesc(
            UUID historicalPostId, String pageId, Pageable pageable);
}
