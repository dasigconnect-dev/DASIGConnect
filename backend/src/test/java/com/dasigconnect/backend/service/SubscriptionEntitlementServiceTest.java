package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.exception.SubscriptionLimitExceededException;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubscriptionActionReservation;
import com.dasigconnect.backend.model.entity.SubscriptionActionReservationStatus;
import com.dasigconnect.backend.model.entity.SubscriptionPlan;
import com.dasigconnect.backend.model.entity.SubscriptionUsageEventType;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.SubscriptionActionReservationRepository;
import com.dasigconnect.backend.repository.SubscriptionUsageEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionEntitlementServiceTest {

    @Mock private SubscriptionActionReservationRepository reservationRepository;
    @Mock private SubscriptionUsageEventRepository usageRepository;
    @Mock private OrganizationSubscriptionRepository subscriptionRepository;
    @Mock private SubmissionRepository submissionRepository;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setOrganizationId(organizationId);
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);
        lenient().when(submissionRepository.findByIdWithInstitution(submissionId))
                .thenReturn(Optional.of(submission));
        lenient().when(subscriptionRepository.findByOrganizationIdForUpdate(organizationId))
                .thenReturn(Optional.of(subscription()));
        lenient().when(reservationRepository.findByIdempotencyKeyForUpdate(any()))
                .thenReturn(Optional.empty());
        lenient().when(reservationRepository.save(any())).thenAnswer(invocation -> {
            SubscriptionActionReservation reservation = invocation.getArgument(0);
            if (reservation.getId() == null) reservation.setId(UUID.randomUUID());
            return reservation;
        });
    }

    @Test
    void reserveCaption_accountsForMonthlyAndDailyUsage() {
        when(usageRepository.sumConsumption(
                eq(organizationId), eq(SubscriptionUsageEventType.AI_CAPTION.name()), any(), any()))
                .thenReturn(10L);
        when(reservationRepository.sumActiveOrganizationReservations(
                eq(organizationId), eq(SubscriptionUsageEventType.AI_CAPTION), any(), any(), any()))
                .thenReturn(2L);
        when(usageRepository.sumUserConsumption(
                eq(organizationId), eq(userId), eq(SubscriptionUsageEventType.AI_CAPTION.name()), any(), any()))
                .thenReturn(1L);

        SubscriptionEntitlementService.ActionReservation result = service(true)
                .reserveForSubmission(
                        submissionId, userId, SubscriptionUsageEventType.AI_CAPTION,
                        "caption-request", "AI_CAPTION_REQUEST");

        assertThat(result.reservationId()).isNotNull();
        assertThat(result.remaining()).isEqualTo(3L);
        ArgumentCaptor<SubscriptionActionReservation> captor =
                ArgumentCaptor.forClass(SubscriptionActionReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(SubscriptionActionReservationStatus.RESERVED);
        assertThat(captor.getValue().getEventType())
                .isEqualTo(SubscriptionUsageEventType.AI_CAPTION);
    }

    @Test
    void reserve_whenMonthlyLimitIsReached_rejectsBeforeSaving() {
        when(usageRepository.sumConsumption(
                eq(organizationId), eq(SubscriptionUsageEventType.AI_MEDIA_SUGGESTION.name()), any(), any()))
                .thenReturn(20L);

        assertThatThrownBy(() -> service(true).reserveForSubmission(
                submissionId, userId, SubscriptionUsageEventType.AI_MEDIA_SUGGESTION,
                "media-request", "AI_MEDIA_SUGGESTION_REQUEST"))
                .isInstanceOf(SubscriptionLimitExceededException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserveCaption_whenDailyUserLimitIsReached_rejectsBeforeSaving() {
        when(usageRepository.sumUserConsumption(
                eq(organizationId), eq(userId), eq(SubscriptionUsageEventType.AI_CAPTION.name()), any(), any()))
                .thenReturn(5L);

        assertThatThrownBy(() -> service(true).reserveForSubmission(
                submissionId, userId, SubscriptionUsageEventType.AI_CAPTION,
                "caption-request", "AI_CAPTION_REQUEST"))
                .isInstanceOf(SubscriptionLimitExceededException.class)
                .hasMessageContaining("dailyAiCaptions");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_whenEnforcementIsDisabled_recordsProjectedOverage() {
        when(usageRepository.sumConsumption(
                eq(organizationId), eq(SubscriptionUsageEventType.AI_MEDIA_SUGGESTION.name()), any(), any()))
                .thenReturn(20L);

        SubscriptionEntitlementService.ActionReservation result = service(false)
                .reserveForSubmission(
                        submissionId, userId, SubscriptionUsageEventType.AI_MEDIA_SUGGESTION,
                        "media-request", "AI_MEDIA_SUGGESTION_REQUEST");

        assertThat(result.remaining()).isZero();
        verify(reservationRepository).save(any());
    }

    @Test
    void reserveCaption_duplicateRequestReturnsExistingReservationAndDailyAllowance() {
        String requestKey = "caption-request";
        String idempotencyKey = "ai-caption:" + UUID.nameUUIDFromBytes(
                (submissionId + ":" + requestKey).getBytes(StandardCharsets.UTF_8));
        SubscriptionActionReservation reservation = activeReservation();
        reservation.setIdempotencyKey(idempotencyKey);
        when(reservationRepository.findByIdempotencyKeyForUpdate(idempotencyKey))
                .thenReturn(Optional.of(reservation));
        when(reservationRepository.sumActiveOrganizationReservations(
                eq(organizationId), eq(SubscriptionUsageEventType.AI_CAPTION), any(), any(), any()))
                .thenReturn(1L);
        when(usageRepository.sumUserConsumption(
                eq(organizationId), eq(userId), eq(SubscriptionUsageEventType.AI_CAPTION.name()), any(), any()))
                .thenReturn(3L);
        when(reservationRepository.sumActiveUserReservations(
                eq(organizationId), eq(userId), eq(SubscriptionUsageEventType.AI_CAPTION), any(), any(), any()))
                .thenReturn(1L);

        SubscriptionEntitlementService.ActionReservation result = service(true)
                .reserveForSubmission(
                        submissionId, userId, SubscriptionUsageEventType.AI_CAPTION,
                        requestKey, "AI_CAPTION_REQUEST");

        assertThat(result.reservationId()).isEqualTo(reservation.getId());
        assertThat(result.remaining()).isEqualTo(1L);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void consume_insertsUsageOnceAndMarksReservationConsumed() {
        SubscriptionActionReservation reservation = activeReservation();
        when(reservationRepository.findByIdForUpdate(reservation.getId()))
                .thenReturn(Optional.of(reservation));

        service(true).consume(reservation.getId());

        verify(usageRepository).insertIfAbsent(
                any(), eq(organizationId), eq(institutionId), eq(userId),
                eq(SubscriptionUsageEventType.AI_CAPTION.name()), eq(1L), eq("COUNT"),
                eq("ai-caption:key"), eq("AI_CAPTION_REQUEST"), eq(submissionId), any());
        assertThat(reservation.getStatus())
                .isEqualTo(SubscriptionActionReservationStatus.CONSUMED);
        assertThat(reservation.getConsumedAt()).isNotNull();
    }

    @Test
    void release_marksActiveReservationReleasedWithoutUsage() {
        SubscriptionActionReservation reservation = activeReservation();
        when(reservationRepository.findByIdForUpdate(reservation.getId()))
                .thenReturn(Optional.of(reservation));

        service(true).release(reservation.getId());

        assertThat(reservation.getStatus())
                .isEqualTo(SubscriptionActionReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isNotNull();
        verify(usageRepository, never()).insertIfAbsent(
                any(), any(), any(), any(), any(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void institutionAndSeatCapacityAreEnforced() {
        when(usageRepository.currentInstitutionCount(organizationId)).thenReturn(3L);
        when(usageRepository.currentSeatCommitmentCount(eq(organizationId), any())).thenReturn(15L);
        when(usageRepository.hasSeatCommitment(eq(organizationId), eq("new@test.edu"), any()))
                .thenReturn(false);

        SubscriptionEntitlementService service = service(true);
        assertThatThrownBy(() -> service.assertCanCreateInstitution(organizationId))
                .isInstanceOf(SubscriptionLimitExceededException.class);
        assertThatThrownBy(() -> service.assertCanCommitSeat(organizationId, "new@test.edu"))
                .isInstanceOf(SubscriptionLimitExceededException.class);
    }

    private SubscriptionEntitlementService service(boolean enforcementEnabled) {
        return new SubscriptionEntitlementService(
                reservationRepository,
                usageRepository,
                subscriptionRepository,
                submissionRepository,
                enforcementEnabled,
                15);
    }

    private OrganizationSubscription subscription() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setInstitutionLimit(3);
        plan.setActiveUserLimit(15);
        plan.setMonthlySubmissionLimit(10);
        plan.setMonthlyAiCaptionLimit(20);
        plan.setMonthlyAiMediaSuggestionLimit(20);
        plan.setPerUserDailyCaptionLimit(5);
        OrganizationSubscription subscription = new OrganizationSubscription();
        subscription.setPlan(plan);
        subscription.setCurrentPeriodStart(Instant.now().minusSeconds(3_600));
        subscription.setCurrentPeriodEnd(Instant.now().plusSeconds(3_600));
        return subscription;
    }

    private SubscriptionActionReservation activeReservation() {
        SubscriptionActionReservation reservation = new SubscriptionActionReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setOrganizationId(organizationId);
        reservation.setInstitutionId(institutionId);
        reservation.setUserId(userId);
        reservation.setEventType(SubscriptionUsageEventType.AI_CAPTION);
        reservation.setQuantity(1);
        reservation.setIdempotencyKey("ai-caption:key");
        reservation.setSourceType("AI_CAPTION_REQUEST");
        reservation.setSourceId(submissionId);
        reservation.setStatus(SubscriptionActionReservationStatus.RESERVED);
        reservation.setReservedAt(Instant.now());
        reservation.setExpiresAt(Instant.now().plusSeconds(300));
        return reservation;
    }
}
