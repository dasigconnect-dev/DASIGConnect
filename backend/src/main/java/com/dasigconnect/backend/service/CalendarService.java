package com.dasigconnect.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.dto.calendar.CalendarEventDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

/**
 * Builds role-scoped calendar event lists from submissions with a scheduled slot.
 *
 * Admin: full detail for all institutions, all statuses.
 * Contributor / Validator: full detail for own institution, timing-only (masked) for others.
 *   Only calendar-visible statuses (scheduled, publishing, published variants) are included —
 *   draft, pending, in-review, failed, and rejected rows are never returned to non-admins.
 */
@Service
@Transactional(readOnly = true)
public class CalendarService {

    private final SubmissionRepository submissionRepository;
    private final InstitutionRepository institutionRepository;

    public CalendarService(SubmissionRepository submissionRepository, InstitutionRepository institutionRepository) {
        this.submissionRepository = submissionRepository;
        this.institutionRepository = institutionRepository;
    }

    public List<CalendarEventDto> getCalendarEvents(JwtUserDetails user) {
        return switch (user.role().toLowerCase()) {
            case "super_administrator" -> getAdminCalendar();
            default -> getScopedCalendar(user);
        };
    }

    private List<CalendarEventDto> getAdminCalendar() {
        return submissionRepository.findAllWithScheduledSlot()
                .stream()
                .map(CalendarEventDto::full)
                .toList();
    }

    private List<CalendarEventDto> getScopedCalendar(JwtUserDetails user) {
        UUID dasigCentralVisayasId = institutionRepository.findByNameIgnoreCase("DASIG Central Visayas")
                .map(Institution::getId)
                .orElse(null);

        List<Submission> all = submissionRepository.findAllCalendarVisibleSlots();
        return all.stream()
                .map(s -> {
                    UUID submissionInstId = s.getInstitution() != null ? s.getInstitution().getId() : null;
                    boolean ownInstitution = user.institutionId() != null
                            && user.institutionId().equals(submissionInstId);
                    boolean isDasigCentralVisayas = dasigCentralVisayasId != null
                            && dasigCentralVisayasId.equals(submissionInstId);

                    return (ownInstitution || isDasigCentralVisayas)
                            ? CalendarEventDto.full(s)
                            : CalendarEventDto.masked(s);
                })
                .toList();
    }
}
