package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.AiInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AiInteractionLogRepository extends JpaRepository<AiInteractionLog, UUID> {

    /** Latest event of one type/action for a submission — e.g. the most recent album suggestion. */
    Optional<AiInteractionLog> findFirstBySubmissionIdAndInteractionTypeAndActionTakenOrderByCreatedAtDesc(
            UUID submissionId, String interactionType, String actionTaken);

    boolean existsBySubmissionIdAndInteractionTypeAndActionTakenIn(
            UUID submissionId, String interactionType, java.util.Collection<String> actionTaken);
}
