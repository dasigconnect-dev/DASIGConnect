package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.model.entity.SlotReservation;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@ExtendWith(MockitoExtension.class)
class EngagementRecommendationServiceTest {

    @Mock private SlotReservationRepository slotReservationRepository;
    @Mock private GuardRailSettingsService guardRailSettingsService;
    private EngagementRecommendationService service;
    private JwtUserDetails user;

    @BeforeEach
    void setUp() {
        service = new EngagementRecommendationService(
                slotReservationRepository, guardRailSettingsService);
        user = new JwtUserDetails(
                UUID.randomUUID(), "contributor@example.com", "contributor", UUID.randomUUID());
        lenient().when(guardRailSettingsService.enforced()).thenReturn(true);
        lenient().when(guardRailSettingsService.postingWindowStartHour()).thenReturn(8);
        lenient().when(guardRailSettingsService.postingWindowEndHour()).thenReturn(18);
        lenient().when(guardRailSettingsService.conflictBufferMinutes()).thenReturn(30);
        lenient().when(guardRailSettingsService.minimumLeadTimeHours()).thenReturn(2);
        lenient().when(guardRailSettingsService.maximumLeadTimeDays()).thenReturn(30);
        lenient().when(slotReservationRepository.findActiveInWindow(any(), any())).thenReturn(List.of());
    }

    @Test
    void selectedDateReturnsEveryOpenIntervalDuringReviewerHours() {
        LocalDate date = futureDate();

        var result = service.recommend(user, null, null, date);

        assertThat(result.available()).isTrue();
        assertThat(result.source()).isEqualTo("AVAILABILITY");
        assertThat(result.sampleSize()).isZero();
        assertThat(result.slots()).hasSize(20);
        assertThat(result.slots()).allMatch(slot ->
                slot.scheduledAt().atZone(EngagementRecommendationService.PAGE_ZONE).toLocalDate().equals(date));
        assertThat(result.slots().getFirst().scheduledAt()
                .atZone(EngagementRecommendationService.PAGE_ZONE).toLocalTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(result.slots().getLast().scheduledAt()
                .atZone(EngagementRecommendationService.PAGE_ZONE).toLocalTime()).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void twentyMinuteIntervalAllowsSlotExactlyTwentyMinutesAfterReservation() {
        LocalDate date = futureDate();
        when(guardRailSettingsService.conflictBufferMinutes()).thenReturn(20);
        when(slotReservationRepository.findActiveInWindow(any(), any())).thenReturn(List.of(
                reservation(date, LocalTime.of(8, 20), UUID.randomUUID())));

        var result = service.recommend(user, null, null, date);
        List<LocalTime> times = result.slots().stream()
                .map(slot -> slot.scheduledAt().atZone(
                        EngagementRecommendationService.PAGE_ZONE).toLocalTime())
                .toList();

        assertThat(times).contains(LocalTime.of(8, 0), LocalTime.of(8, 40));
        assertThat(times).doesNotContain(LocalTime.of(8, 20));
    }

    @Test
    void changingDateChangesEveryReturnedSlotToThatDate() {
        LocalDate firstDate = futureDate();
        LocalDate secondDate = firstDate.plusDays(1);

        var first = service.recommend(user, null, null, firstDate);
        var second = service.recommend(user, null, null, secondDate);

        assertThat(first.slots()).allMatch(slot -> slot.scheduledAt().atZone(
                EngagementRecommendationService.PAGE_ZONE).toLocalDate().equals(firstDate));
        assertThat(second.slots()).allMatch(slot -> slot.scheduledAt().atZone(
                EngagementRecommendationService.PAGE_ZONE).toLocalDate().equals(secondDate));
    }

    @Test
    void highlightsTheAvailableSlotClosestToThePhilippineEducationBenchmark() {
        LocalDate monday = futureDay(DayOfWeek.MONDAY);

        var result = service.recommend(user, null, null, monday);
        var best = result.slots().stream()
                .max(java.util.Comparator.comparingDouble(slot -> slot.score()))
                .orElseThrow();

        assertThat(best.scheduledAt().atZone(EngagementRecommendationService.PAGE_ZONE).toLocalTime())
                .isEqualTo(LocalTime.of(14, 0));
        assertThat(best.score()).isEqualTo(1.0);
        assertThat(best.windowLabel()).contains("Philippine Facebook benchmark", "Monday");
    }

    @Test
    void occupiedBenchmarkTimeFallsBackToTheNearestAvailableSlot() {
        LocalDate monday = futureDay(DayOfWeek.MONDAY);
        when(slotReservationRepository.findActiveInWindow(any(), any())).thenReturn(List.of(
                reservation(monday, LocalTime.of(14, 0), UUID.randomUUID())));

        var result = service.recommend(user, null, null, monday);
        var best = result.slots().stream()
                .max(java.util.Comparator.comparingDouble(slot -> slot.score()))
                .orElseThrow();
        LocalTime bestTime = best.scheduledAt().atZone(
                EngagementRecommendationService.PAGE_ZONE).toLocalTime();

        assertThat(bestTime).isIn(LocalTime.of(13, 30), LocalTime.of(14, 30));
    }

    @Test
    void configuredPostingWindowIsIntersectedWithReviewerHours() {
        when(guardRailSettingsService.postingWindowStartHour()).thenReturn(10);
        when(guardRailSettingsService.postingWindowEndHour()).thenReturn(14);

        var result = service.recommend(user, null, null, futureDate());

        assertThat(result.slots()).allMatch(slot -> {
            int hour = slot.scheduledAt().atZone(EngagementRecommendationService.PAGE_ZONE).getHour();
            return hour >= 10 && hour < 14;
        });
    }

    @Test
    void guardRailsOffShowsFullDayInThirtyMinuteSteps() {
        when(guardRailSettingsService.enforced()).thenReturn(false);
        when(guardRailSettingsService.postingWindowStartHour()).thenReturn(20);
        when(guardRailSettingsService.postingWindowEndHour()).thenReturn(23);
        when(guardRailSettingsService.conflictBufferMinutes()).thenReturn(20);

        var result = service.recommend(user, null, null, futureDate());

        assertThat(result.slots()).hasSize(48);
        assertThat(result.slots().getFirst().scheduledAt()
                .atZone(EngagementRecommendationService.PAGE_ZONE).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(result.slots().getLast().scheduledAt()
                .atZone(EngagementRecommendationService.PAGE_ZONE).toLocalTime()).isEqualTo(LocalTime.of(23, 30));
        assertThat(result.notice()).contains("full day", "30-minute steps", "Reviewer coverage");
    }

    @Test
    void guardRailsOffRemovesOnlyTheExactOccupiedTime() {
        LocalDate date = futureDate();
        when(guardRailSettingsService.enforced()).thenReturn(false);
        when(slotReservationRepository.findActiveInWindow(any(), any())).thenReturn(List.of(
                reservation(date, LocalTime.of(8, 30), UUID.randomUUID())));

        var result = service.recommend(user, null, null, date);
        List<LocalTime> times = result.slots().stream()
                .map(slot -> slot.scheduledAt().atZone(
                        EngagementRecommendationService.PAGE_ZONE).toLocalTime())
                .toList();

        assertThat(times).doesNotContain(LocalTime.of(8, 30));
        assertThat(times).contains(LocalTime.of(8, 0), LocalTime.of(9, 0));
    }

    @Test
    void currentSubmissionReservationDoesNotHideItsOwnTime() {
        LocalDate date = futureDate();
        UUID submissionId = UUID.randomUUID();
        when(slotReservationRepository.findActiveInWindow(any(), any())).thenReturn(List.of(
                reservation(date, LocalTime.of(8, 30), submissionId)));

        var result = service.recommend(user, null, submissionId, date);

        assertThat(result.slots()).anyMatch(slot -> slot.scheduledAt().atZone(
                EngagementRecommendationService.PAGE_ZONE).toLocalTime().equals(LocalTime.of(8, 30)));
    }

    @Test
    void pastDateReturnsNoTimes() {
        var result = service.recommend(
                user, null, null, LocalDate.now(EngagementRecommendationService.PAGE_ZONE).minusDays(1));

        assertThat(result.available()).isTrue();
        assertThat(result.slots()).isEmpty();
    }

    @Test
    void repositoryFailureMarksAvailabilityUnavailable() {
        when(slotReservationRepository.findActiveInWindow(any(), any()))
                .thenThrow(new IllegalStateException("offline"));

        var result = service.recommend(user, null, null, futureDate());

        assertThat(result.available()).isFalse();
        assertThat(result.source()).isEqualTo("UNAVAILABLE");
    }

    private static LocalDate futureDate() {
        return LocalDate.now(EngagementRecommendationService.PAGE_ZONE).plusDays(2);
    }

    private static LocalDate futureDay(DayOfWeek dayOfWeek) {
        return LocalDate.now(EngagementRecommendationService.PAGE_ZONE)
                .plusDays(2)
                .with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }

    private static SlotReservation reservation(LocalDate date, LocalTime time, UUID submissionId) {
        Submission submission = new Submission();
        submission.setId(submissionId);
        SlotReservation reservation = new SlotReservation();
        reservation.setSubmission(submission);
        Instant scheduledAt = date.atTime(time).atZone(
                EngagementRecommendationService.PAGE_ZONE).toInstant();
        reservation.setScheduledAt(scheduledAt);
        return reservation;
    }
}
