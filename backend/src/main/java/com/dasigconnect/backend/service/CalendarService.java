package com.dasigconnect.backend.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.dto.calendar.CalendarEventDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

/**
 * Builds role-scoped calendar event lists from submissions with a scheduled slot.
 *
 * Admin/Super Administrator: both network-wide roles — full detail for all
 *   institutions, all statuses.
 * Contributor / Validator: full detail for own institution, timing-only (masked) for others.
 *   Only calendar-visible statuses (scheduled, publishing, published variants) are included —
 *   draft, pending, in-review, failed, and rejected rows are never returned to non-admins.
 */
@Service
@Transactional(readOnly = true)
public class CalendarService {

    private final SubmissionRepository submissionRepository;
    private final InstitutionRepository institutionRepository;
    private final SlotReservationRepository slotReservationRepository;

    public CalendarService(
            SubmissionRepository submissionRepository,
            InstitutionRepository institutionRepository,
            SlotReservationRepository slotReservationRepository) {
        this.submissionRepository = submissionRepository;
        this.institutionRepository = institutionRepository;
        this.slotReservationRepository = slotReservationRepository;
    }

    public List<CalendarEventDto> getCalendarEvents(JwtUserDetails user) {
        return switch (user.role().toLowerCase()) {
            case "administrator", "super_administrator" -> getAdminCalendar();
            default -> getScopedCalendar(user);
        };
    }

    private List<CalendarEventDto> getAdminCalendar() {
        List<Submission> submissions = submissionRepository.findAllWithScheduledSlot();
        Set<UUID> lockedIds = lockedSubmissionIds(submissions);
        return submissions.stream()
                .map(s -> {
                    CalendarEventDto dto = CalendarEventDto.full(s);
                    dto.setLocked(lockedIds.contains(s.getId()));
                    return dto;
                })
                .toList();
    }

    private List<CalendarEventDto> getScopedCalendar(JwtUserDetails user) {
        UUID dasigCentralVisayasId = institutionRepository.findByNameIgnoreCase("DASIG Central Visayas")
                .map(Institution::getId)
                .orElse(null);

        List<Submission> all = submissionRepository.findAllCalendarVisibleSlots();
        Set<UUID> lockedIds = lockedSubmissionIds(all);
        return all.stream()
                .map(s -> {
                    UUID submissionInstId = s.getInstitution() != null ? s.getInstitution().getId() : null;
                    boolean ownInstitution = user.institutionId() != null
                            && user.institutionId().equals(submissionInstId);
                    boolean isDasigCentralVisayas = dasigCentralVisayasId != null
                            && dasigCentralVisayasId.equals(submissionInstId);

                    CalendarEventDto dto = (ownInstitution || isDasigCentralVisayas)
                            ? CalendarEventDto.full(s)
                            : CalendarEventDto.masked(s);
                    dto.setLocked(lockedIds.contains(s.getId()));
                    return dto;
                })
                .toList();
    }

    private Set<UUID> lockedSubmissionIds(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = submissions.stream().map(Submission::getId).toList();
        return Set.copyOf(slotReservationRepository.findLockedSubmissionIds(ids));
    }
}
