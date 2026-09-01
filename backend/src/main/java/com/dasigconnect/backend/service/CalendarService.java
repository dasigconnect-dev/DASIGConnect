package com.dasigconnect.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Admin/Admin: both network-wide roles — full detail for all
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
            case "moderator", "admin" -> getAdminCalendar();
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

        // 1. Network bucket: scheduled / published variants for every institution —
        //    full for own institution + DASIG Central Visayas, masked elsewhere.
        List<Submission> network = submissionRepository.findAllCalendarVisibleSlots();

        // 2. Own-workflow bucket: the caller's own failed / pending / in-review /
        //    missed-review submissions, always in full.
        List<Submission> ownWorkflow = user.userId() != null
                ? submissionRepository.findOwnCalendarWorkflowSlots(user.userId())
                : List.of();

        List<Submission> combined = new ArrayList<>(network);
        combined.addAll(ownWorkflow);
        Set<UUID> lockedIds = lockedSubmissionIds(combined);

        Map<UUID, CalendarEventDto> byId = new LinkedHashMap<>();

        for (Submission s : network) {
            UUID submissionInstId = s.getInstitution() != null ? s.getInstitution().getId() : null;
            boolean ownInstitution = user.institutionId() != null
                    && user.institutionId().equals(submissionInstId);
            boolean isDasigCentralVisayas = dasigCentralVisayasId != null
                    && dasigCentralVisayasId.equals(submissionInstId);

            CalendarEventDto dto = (ownInstitution || isDasigCentralVisayas)
                    ? CalendarEventDto.full(s)
                    : CalendarEventDto.masked(s);
            dto.setLocked(lockedIds.contains(s.getId()));
            byId.put(s.getId(), dto);
        }

        for (Submission s : ownWorkflow) {
            CalendarEventDto dto = CalendarEventDto.full(s);
            dto.setMine(true);
            dto.setLocked(lockedIds.contains(s.getId()));
            byId.put(s.getId(), dto); // own-workflow entry wins on the (impossible) id clash
        }

        return List.copyOf(byId.values());
    }

    private Set<UUID> lockedSubmissionIds(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = submissions.stream().map(Submission::getId).toList();
        return Set.copyOf(slotReservationRepository.findLockedSubmissionIds(ids));
    }
}
