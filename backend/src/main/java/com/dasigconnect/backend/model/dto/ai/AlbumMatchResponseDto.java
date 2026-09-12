package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

/**
 * Result of album Auto-Match (UC-1.7).
 *
 * <ul>
 *   <li>{@code confident} — one candidate cleared the high-confidence bar; the
 *       composer applies it directly and shows the reasons as a badge.</li>
 *   <li>{@code ambiguous} — one or more candidates cleared the lower bar but
 *       not the confident one; the composer shows them as ranked choices
 *       instead of auto-applying.</li>
 *   <li>{@code none} — no existing root album scored high enough (or the
 *       institution has none yet); the composer falls back to manual
 *       select/create, same as before Auto-Match existed.</li>
 * </ul>
 */
public class AlbumMatchResponseDto {

    public enum Status { confident, ambiguous, none }

    private Status status;
    private List<AlbumMatchCandidateDto> candidates;

    public static AlbumMatchResponseDto of(Status status, List<AlbumMatchCandidateDto> candidates) {
        AlbumMatchResponseDto dto = new AlbumMatchResponseDto();
        dto.status = status;
        dto.candidates = candidates == null ? List.of() : List.copyOf(candidates);
        return dto;
    }

    public Status getStatus() { return status; }
    public List<AlbumMatchCandidateDto> getCandidates() { return candidates; }
}
