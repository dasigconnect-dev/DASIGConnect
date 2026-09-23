package com.dasigconnect.backend.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dasigconnect.backend.event.FastTrackSubmissionEvent;
import com.dasigconnect.backend.event.SubmissionPendingEvent;
import com.dasigconnect.backend.model.entity.AiInteractionLog;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.AiInteractionLogRepository;

/**
 * Records adoption events for the Analytics "AI Feature Adoption" panel for
 * features whose outcome isn't a simple accept/dismiss click:
 *
 * <ul>
 *   <li><b>Album Auto-Match</b> — each suggestion records the latest proposal;
 *       on submit, one outcome per submission: did the post keep an album the
 *       AI proposed?</li>
 *   <li><b>Template from Top Posts</b> — drafts generated vs. drafts saved.</li>
 * </ul>
 *
 * <p>Every write runs in its own transaction so tracking can never roll back
 * the action being tracked; callers must still catch failures.
 */
@Service
public class AiAdoptionTrackingService {

    private static final Logger log = LoggerFactory.getLogger(AiAdoptionTrackingService.class);

    static final String ALBUM_MATCH = "album_match";
    static final String TEMPLATE_DRAFT = "template_draft";
    static final String SUGGESTED = "suggested";
    static final String KEPT = "kept";
    static final String CHANGED = "changed";
    static final String GENERATED = "generated";
    static final String SAVED = "saved";

    private final AiInteractionLogRepository repository;

    public AiAdoptionTrackingService(AiInteractionLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records what Auto-Match proposed for a draft ({@code confident} = the one
     * album it would auto-apply, {@code ambiguous} = the ranked choices). Only
     * the latest proposal matters, so an identical repeat isn't stored again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAlbumSuggestion(UUID submissionId, UUID institutionId, String status, List<String> albumNames) {
        if (submissionId == null || albumNames == null || albumNames.isEmpty()) return;
        String proposal = status + "\n" + String.join("\n", albumNames);
        boolean unchanged = repository
                .findFirstBySubmissionIdAndInteractionTypeAndActionTakenOrderByCreatedAtDesc(
                        submissionId, ALBUM_MATCH, SUGGESTED)
                .map(previous -> proposal.equals(previous.getSuggestedValue()))
                .orElse(false);
        if (unchanged) return;
        save(submissionId, institutionId, ALBUM_MATCH, SUGGESTED, proposal);
    }

    /**
     * On submit: if Auto-Match proposed anything for this draft, record whether
     * the final album is one it proposed. Counted once per submission — a
     * resubmission after revision doesn't add a second outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAlbumOutcome(UUID submissionId, UUID institutionId, String finalAlbumName) {
        if (submissionId == null) return;
        if (repository.existsBySubmissionIdAndInteractionTypeAndActionTakenIn(
                submissionId, ALBUM_MATCH, List.of(KEPT, CHANGED))) {
            return;
        }
        repository.findFirstBySubmissionIdAndInteractionTypeAndActionTakenOrderByCreatedAtDesc(
                        submissionId, ALBUM_MATCH, SUGGESTED)
                .ifPresent(suggestion -> {
                    String outcome = proposedAlbums(suggestion.getSuggestedValue()).stream()
                            .anyMatch(name -> sameAlbum(name, finalAlbumName)) ? KEPT : CHANGED;
                    save(submissionId, institutionId, ALBUM_MATCH, outcome, suggestion.getSuggestedValue());
                });
    }

    // Album outcome is judged once the submit has committed, so tracking can
    // never affect the submit itself (same pattern as NotificationEventListener).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionPending(SubmissionPendingEvent event) {
        trackAlbumOutcome(event.submission());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFastTrackSubmission(FastTrackSubmissionEvent event) {
        trackAlbumOutcome(event.submission());
    }

    private void trackAlbumOutcome(Submission submission) {
        try {
            UUID institutionId = submission.getInstitution() != null ? submission.getInstitution().getId() : null;
            recordAlbumOutcome(submission.getId(), institutionId, submission.getAlbumName());
        } catch (RuntimeException ex) {
            log.warn("Album Auto-Match outcome not recorded for submission {}: {}", submission.getId(), ex.getMessage());
        }
    }

    /** A Top Posts template draft was generated ({@code generated}) or saved ({@code saved}). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTemplateDraft(UUID institutionId, String action) {
        save(null, institutionId, TEMPLATE_DRAFT, action, null);
    }

    /** Album names from a stored proposal ("status\nName\nName…"); the status line is skipped. */
    static List<String> proposedAlbums(String proposal) {
        if (proposal == null || proposal.isBlank()) return List.of();
        List<String> lines = proposal.lines().toList();
        return lines.size() <= 1 ? List.of() : lines.subList(1, lines.size());
    }

    /** Albums resolve by name case-insensitively on submit, so compare the same way. */
    private static boolean sameAlbum(String proposed, String finalName) {
        return finalName != null
                && Objects.equals(proposed.strip().toLowerCase(Locale.ROOT), finalName.strip().toLowerCase(Locale.ROOT));
    }

    private void save(UUID submissionId, UUID institutionId, String type, String action, String suggestedValue) {
        AiInteractionLog entry = new AiInteractionLog();
        entry.setSubmissionId(submissionId);
        entry.setInstitutionId(institutionId);
        entry.setInteractionType(type);
        entry.setActionTaken(action);
        entry.setSuggestedValue(suggestedValue);
        repository.save(entry);
    }
}
