package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.dto.engagement.EngagementRecommendationDto;
import com.dasigconnect.backend.model.dto.engagement.EngagementRecommendationDto.RecommendedSlotDto;
import com.dasigconnect.backend.model.entity.SlotReservation;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@Service
@Transactional(readOnly = true)
public class EngagementRecommendationService {

    static final ZoneId PAGE_ZONE = ZoneId.of("Asia/Manila");
    private static final int REVIEWER_WORK_START_HOUR = 8;
    private static final int REVIEWER_WORK_END_HOUR = 18;
    private static final int FULL_DAY_START_HOUR = 0;
    private static final int FULL_DAY_END_HOUR = 24;
    private static final int DEFAULT_SLOT_STEP_MINUTES = 30;
    // Local-clock baselines for Facebook audiences in the Philippines. The
    // education-specific peaks are Monday 1-3 PM, Tuesday 5 PM, and Thursday
    // 11 AM-1 PM; the remaining targets use the broader Philippine weekday
    // and weekend engagement windows. Availability and guard rails always win.
    private static final int MONDAY_TARGET_MINUTE = 14 * 60;
    private static final int TUESDAY_TARGET_MINUTE = 17 * 60;
    private static final int WEDNESDAY_TARGET_MINUTE = 11 * 60;
    private static final int THURSDAY_TARGET_MINUTE = 12 * 60;
    private static final int FRIDAY_TARGET_MINUTE = 10 * 60;
    private static final int SATURDAY_TARGET_MINUTE = 9 * 60;
    private static final int SUNDAY_TARGET_MINUTE = 9 * 60;
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final SlotReservationRepository slotReservationRepository;
    private final GuardRailSettingsService guardRailSettingsService;

    public EngagementRecommendationService(
            SlotReservationRepository slotReservationRepository,
            GuardRailSettingsService guardRailSettingsService) {
        this.slotReservationRepository = slotReservationRepository;
        this.guardRailSettingsService = guardRailSettingsService;
    }

    public EngagementRecommendationDto recommend(
            JwtUserDetails user,
            UUID requestedInstitutionId,
            UUID excludeSubmissionId,
            LocalDate requestedDate) {
        if (requestedDate == null) {
            return availabilityResult("Choose a date to see its available times.", List.of());
        }

        try {
            AvailabilityPolicy policy = currentPolicy();
            LocalDate today = LocalDate.now(PAGE_ZONE);
            if (requestedDate.isBefore(today)
                    || (policy.enforced()
                    && requestedDate.isAfter(today.plusDays(policy.maximumLeadTimeDays())))) {
                return availabilityResult(
                        "No available times fall within the scheduling horizon for "
                                + requestedDate.format(DATE_LABEL) + ".",
                        List.of());
            }

            int startHour = policy.candidateStartHour();
            int endHour = policy.candidateEndHour();
            if (startHour >= endHour) {
                return availabilityResult(
                        "The configured posting window does not overlap reviewer coverage from 8 AM to 6 PM.",
                        List.of());
            }

            Duration conflictBuffer = policy.enforced()
                    ? Duration.ofMinutes(policy.conflictBufferMinutes())
                    : Duration.ZERO;
            Instant dayStart = requestedDate.atStartOfDay(PAGE_ZONE).toInstant();
            Instant dayEnd = requestedDate.plusDays(1).atStartOfDay(PAGE_ZONE).toInstant();
            List<SlotReservation> reservations = slotReservationRepository.findActiveInWindow(
                    dayStart.minus(conflictBuffer), dayEnd.plus(conflictBuffer));

            Instant earliest = policy.enforced()
                    ? Instant.now().plus(Duration.ofHours(policy.minimumLeadTimeHours()))
                    : Instant.now();
            int stepMinutes = policy.enforced() && policy.conflictBufferMinutes() > 0
                    ? policy.conflictBufferMinutes()
                    : DEFAULT_SLOT_STEP_MINUTES;
            LocalDateTime cursor = requestedDate.atTime(startHour, 0);
            LocalDateTime endExclusive = endHour == FULL_DAY_END_HOUR
                    ? requestedDate.plusDays(1).atStartOfDay()
                    : requestedDate.atTime(endHour, 0);
            List<RecommendedSlotDto> slots = new ArrayList<>();

            while (cursor.isBefore(endExclusive)) {
                Instant candidate = cursor.atZone(PAGE_ZONE).toInstant();
                if (!candidate.isBefore(earliest)
                        && isCalendarAvailable(candidate, reservations, excludeSubmissionId, conflictBuffer)) {
                    slots.add(new RecommendedSlotDto(
                            candidate,
                            benchmarkLabel(requestedDate.getDayOfWeek()),
                            benchmarkScore(cursor),
                            List.of()));
                }
                cursor = cursor.plusMinutes(stepMinutes);
            }

            String notice = policy.enforced()
                    ? "Available posting times for " + requestedDate.format(DATE_LABEL)
                            + " between " + displayHour(startHour) + " and " + displayHour(endHour) + "."
                    : "Showing open posting times for "
                            + requestedDate.format(DATE_LABEL)
                            + ". Reviewers are typically active from 8 AM to 6 PM.";
            return availabilityResult(notice, slots);
        } catch (Exception exception) {
            return new EngagementRecommendationDto(
                    false, "UNAVAILABLE", null, PAGE_ZONE.getId(), 0, List.of());
        }
    }

    private AvailabilityPolicy currentPolicy() {
        return new AvailabilityPolicy(
                guardRailSettingsService.enforced(),
                guardRailSettingsService.postingWindowStartHour(),
                guardRailSettingsService.postingWindowEndHour(),
                Math.max(0, guardRailSettingsService.conflictBufferMinutes()),
                Math.max(0, guardRailSettingsService.minimumLeadTimeHours()),
                Math.max(1, guardRailSettingsService.maximumLeadTimeDays()));
    }

    private static boolean isCalendarAvailable(
            Instant candidate,
            List<SlotReservation> reservations,
            UUID excludeSubmissionId,
            Duration conflictBuffer) {
        return reservations.stream()
                .filter(reservation -> excludeSubmissionId == null
                        || !excludeSubmissionId.equals(reservation.getSubmission().getId()))
                .noneMatch(reservation -> conflictBuffer.isZero()
                        ? reservation.getScheduledAt().equals(candidate)
                        : Duration.between(reservation.getScheduledAt(), candidate)
                                .abs().compareTo(conflictBuffer) < 0);
    }

    private static EngagementRecommendationDto availabilityResult(
            String notice,
            List<RecommendedSlotDto> slots) {
        return new EngagementRecommendationDto(
                true, "AVAILABILITY", notice, PAGE_ZONE.getId(), 0, slots);
    }

    private static String displayHour(int hour) {
        int normalized = hour % 24;
        int twelveHour = normalized % 12 == 0 ? 12 : normalized % 12;
        return twelveHour + (normalized < 12 ? " AM" : " PM");
    }

    private static double benchmarkScore(LocalDateTime candidate) {
        int candidateMinute = candidate.getHour() * 60 + candidate.getMinute();
        int distanceMinutes = Math.abs(candidateMinute - targetMinute(candidate.getDayOfWeek()));
        // A stable 0.5-1.0 proximity score is sufficient for ranking. It does
        // not represent a predicted engagement percentage or alter availability.
        return Math.max(0.5, 1.0 - Math.min(distanceMinutes, 12 * 60) / (12.0 * 60.0) * 0.5);
    }

    private static int targetMinute(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> MONDAY_TARGET_MINUTE;
            case TUESDAY -> TUESDAY_TARGET_MINUTE;
            case WEDNESDAY -> WEDNESDAY_TARGET_MINUTE;
            case THURSDAY -> THURSDAY_TARGET_MINUTE;
            case FRIDAY -> FRIDAY_TARGET_MINUTE;
            case SATURDAY -> SATURDAY_TARGET_MINUTE;
            case SUNDAY -> SUNDAY_TARGET_MINUTE;
        };
    }

    private static String benchmarkLabel(DayOfWeek dayOfWeek) {
        int targetMinute = targetMinute(dayOfWeek);
        return "Philippine Facebook benchmark: around "
                + displayHour(targetMinute / 60)
                + " on " + titleCase(dayOfWeek) + "s";
    }

    private static String titleCase(DayOfWeek dayOfWeek) {
        String value = dayOfWeek.name().toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record AvailabilityPolicy(
            boolean enforced,
            int postingWindowStartHour,
            int postingWindowEndHour,
            int conflictBufferMinutes,
            int minimumLeadTimeHours,
            int maximumLeadTimeDays) {

        int candidateStartHour() {
            return enforced
                    ? Math.max(postingWindowStartHour, REVIEWER_WORK_START_HOUR)
                    : FULL_DAY_START_HOUR;
        }

        int candidateEndHour() {
            return enforced
                    ? Math.min(postingWindowEndHour, REVIEWER_WORK_END_HOUR)
                    : FULL_DAY_END_HOUR;
        }
    }
}
