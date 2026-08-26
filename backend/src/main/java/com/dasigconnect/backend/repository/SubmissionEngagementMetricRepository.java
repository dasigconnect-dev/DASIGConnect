package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.dasigconnect.backend.model.entity.SubmissionEngagementMetric;

public interface SubmissionEngagementMetricRepository extends JpaRepository<SubmissionEngagementMetric, UUID> {

    Optional<SubmissionEngagementMetric> findBySubmissionId(UUID submissionId);

    /**
     * Published submissions whose engagement metrics were never synced, up to
     * a batch size (via Pageable), oldest published first. Used by
     * SocialEngagementSyncJob.
     */
    @Query("""
        SELECT s.id FROM Submission s
        WHERE s.status IN ('published', 'published_manual', 'admin_direct_post')
          AND s.platformPostId IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM SubmissionEngagementMetric sem
              WHERE sem.submission.id = s.id AND sem.fetchedAt IS NOT NULL
          )
        ORDER BY s.publishedAt ASC
        """)
    List<UUID> findPublishedSubmissionIdsMissingEngagement(Pageable pageable);
}
