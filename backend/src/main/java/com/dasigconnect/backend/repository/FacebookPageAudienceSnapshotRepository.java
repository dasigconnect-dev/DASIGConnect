package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.FacebookPageAudienceSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacebookPageAudienceSnapshotRepository
        extends JpaRepository<FacebookPageAudienceSnapshot, UUID> {

    /** Most recent snapshot — supplies current followers + the latest demographic breakdowns. */
    Optional<FacebookPageAudienceSnapshot> findTopByPageIdOrderByFetchedAtDesc(String pageId);

    /** Snapshots within the selected window, oldest first, for the follower-growth trend. */
    List<FacebookPageAudienceSnapshot> findByPageIdAndFetchedAtAfterOrderByFetchedAtAsc(
            String pageId, Instant after);
}
