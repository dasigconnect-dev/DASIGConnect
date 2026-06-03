package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AiInteractionLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiInteractionLogRepository extends JpaRepository<AiInteractionLog, UUID> {

    /**
     * UC-4.6 precision/acceptance metrics per AI surface. Returns one row per interaction_type:
     * [interactionType, total, applied, dismissed, thumbsUp, thumbsDown]. When {@code institutionId}
     * is null the aggregate is network-wide (administrator scope).
     */
    @Query("""
        SELECT a.interactionType,
               COUNT(a),
               SUM(CASE WHEN a.actionTaken = 'applied' THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.actionTaken = 'dismissed' THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.actionTaken = 'thumbs_up' THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.actionTaken = 'thumbs_down' THEN 1 ELSE 0 END)
        FROM AiInteractionLog a
        WHERE (:institutionId IS NULL OR a.institutionId = :institutionId)
        GROUP BY a.interactionType
        ORDER BY a.interactionType
        """)
    List<Object[]> aggregateBySurface(@Param("institutionId") UUID institutionId);
}
