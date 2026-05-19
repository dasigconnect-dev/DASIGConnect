package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.repository.SlotReservationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
class GuardRailServiceTest {

    @Mock
    private SlotReservationRepository slotReservationRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private GuardRailService guardRailService;

    private UUID institutionId;

    @BeforeEach
    void setUp() {
        institutionId = UUID.randomUUID();

        // Default: no conflicts, no unpublished posts
        lenient().when(slotReservationRepository.existsActiveWithin30Minutes(any(), any())).thenReturn(false);
        lenient().when(slotReservationRepository.countActiveOnDay(any(), any())).thenReturn(0L);
        lenient().when(submissionRepository.countUnpublishedByInstitution(any())).thenReturn(0L);
        lenient().when(slotReservationRepository.findActiveInWindow(any(), any())).thenReturn(List.of());
    }

    // ── GR-H1 ────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GR-H1: No two posts within ±30 minutes")
    class GrH1Tests {

        @Test
        @DisplayName("should block when a reservation exists within 30 minutes")
        void shouldBlock_whenConflictExists() {
            when(slotReservationRepository.existsActiveWithin30Minutes(any(), any())).thenReturn(true);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.getHardBlocks()).anyMatch(v -> v.getCode().equals("GR-H1"));
        }

        @Test
        @DisplayName("should pass when no reservations exist within 30 minutes")
        void shouldPass_whenNoConflict() {
            when(slotReservationRepository.existsActiveWithin30Minutes(any(), any())).thenReturn(false);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.getHardBlocks()).noneMatch(v -> v.getCode().equals("GR-H1"));
        }

        @Test
        @DisplayName("GR-H1 violation should include suggested alternative slots")
        void shouldIncludeSuggestedSlots_onGrH1Violation() {
            when(slotReservationRepository.existsActiveWithin30Minutes(any(), any())).thenReturn(true);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.getHardBlocks())
                    .filteredOn(v -> v.getCode().equals("GR-H1"))
                    .first()
                    .satisfies(v -> assertThat(v.getSuggestedSlots()).isNotNull());
        }
    }

    // ── GR-H2 ────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GR-H2: Scheduled time must be ≥2 hours in the future")
    class GrH2Tests {

        @Test
        @DisplayName("should block when slot is less than 2 hours away")
        void shouldBlock_whenSlotTooSoon() {
            Instant tooSoon = Instant.now().plus(1, ChronoUnit.HOURS);

            GuardRailResult result = guardRailService.validate(institutionId, tooSoon);

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.getHardBlocks()).anyMatch(v -> v.getCode().equals("GR-H2"));
        }

        @Test
        @DisplayName("should block when slot is in the past")
        void shouldBlock_whenSlotInPast() {
            Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

            GuardRailResult result = guardRailService.validate(institutionId, past);

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.getHardBlocks()).anyMatch(v -> v.getCode().equals("GR-H2"));
        }

        @Test
        @DisplayName("should pass when slot is exactly 2 hours and 1 minute away")
        void shouldPass_whenSlotJustBeyond2Hours() {
            Instant justOver = Instant.now().plus(2, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES);

            GuardRailResult result = guardRailService.validate(institutionId, justOver);

            assertThat(result.getHardBlocks()).noneMatch(v -> v.getCode().equals("GR-H2"));
        }
    }

    // ── GR-H3 ────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GR-H3: Scheduled time must be ≤30 days in the future")
    class GrH3Tests {

        @Test
        @DisplayName("should block when slot is more than 30 days away")
        void shouldBlock_whenSlotTooFarInFuture() {
            Instant tooFar = Instant.now().plus(31, ChronoUnit.DAYS);

            GuardRailResult result = guardRailService.validate(institutionId, tooFar);

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.getHardBlocks()).anyMatch(v -> v.getCode().equals("GR-H3"));
        }

        @Test
        @DisplayName("should pass when slot is exactly 29 days away")
        void shouldPass_whenSlotWithin30Days() {
            Instant within30 = Instant.now().plus(29, ChronoUnit.DAYS);

            GuardRailResult result = guardRailService.validate(institutionId, within30);

            assertThat(result.getHardBlocks()).noneMatch(v -> v.getCode().equals("GR-H3"));
        }
    }

    // ── GR-S1 ────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GR-S1: ≤3 scheduled-but-unpublished posts per institution (soft)")
    class GrS1Tests {

        @Test
        @DisplayName("should warn when institution has 3 or more unpublished posts")
        void shouldWarn_whenUnpublishedCountAtThreshold() {
            when(submissionRepository.countUnpublishedByInstitution(institutionId)).thenReturn(3L);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.isBlocked()).isFalse();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getSoftWarnings()).anyMatch(v -> v.getCode().equals("GR-S1"));
        }

        @Test
        @DisplayName("should not warn when institution has fewer than 3 unpublished posts")
        void shouldNotWarn_whenUnpublishedCountBelowThreshold() {
            when(submissionRepository.countUnpublishedByInstitution(institutionId)).thenReturn(2L);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.getSoftWarnings()).noneMatch(v -> v.getCode().equals("GR-S1"));
        }

        @Test
        @DisplayName("GR-S1 warning should not block submission")
        void grS1ShouldBeNonBlocking() {
            when(submissionRepository.countUnpublishedByInstitution(institutionId)).thenReturn(5L);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.isBlocked()).isFalse();
        }
    }

    // ── GR-S2 ────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GR-S2: ≤6 posts per calendar day network-wide (soft)")
    class GrS2Tests {

        @Test
        @DisplayName("should warn when 6 or more posts are scheduled on the same day")
        void shouldWarn_whenDailyCountAtThreshold() {
            when(slotReservationRepository.countActiveOnDay(any(), any())).thenReturn(6L);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.isBlocked()).isFalse();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getSoftWarnings()).anyMatch(v -> v.getCode().equals("GR-S2"));
        }

        @Test
        @DisplayName("should not warn when fewer than 6 posts are on the same day")
        void shouldNotWarn_whenDailyCountBelowThreshold() {
            when(slotReservationRepository.countActiveOnDay(any(), any())).thenReturn(5L);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.getSoftWarnings()).noneMatch(v -> v.getCode().equals("GR-S2"));
        }

        @Test
        @DisplayName("GR-S2 warning should not block submission")
        void grS2ShouldBeNonBlocking() {
            when(slotReservationRepository.countActiveOnDay(any(), any())).thenReturn(10L);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.isBlocked()).isFalse();
        }
    }

    // ── Combined scenarios ────────────────────────────────────────────────────
    @Nested
    @DisplayName("Combined guard rail scenarios")
    class CombinedTests {

        @Test
        @DisplayName("clean result when no rules are violated")
        void shouldBeClean_whenNoRulesViolated() {
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.isClean()).isTrue();
            assertThat(result.isBlocked()).isFalse();
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("multiple hard blocks can fire simultaneously")
        void shouldCollectMultipleHardBlocks() {
            // GR-H1 conflict AND GR-H2 (slot too soon)
            when(slotReservationRepository.existsActiveWithin30Minutes(any(), any())).thenReturn(true);
            Instant tooSoon = Instant.now().plus(30, ChronoUnit.MINUTES);

            GuardRailResult result = guardRailService.validate(institutionId, tooSoon);

            assertThat(result.getHardBlocks()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("hard block and soft warning can coexist")
        void shouldHaveBothBlockAndWarning_whenMultipleRulesFire() {
            when(slotReservationRepository.existsActiveWithin30Minutes(any(), any())).thenReturn(true);
            when(submissionRepository.countUnpublishedByInstitution(any())).thenReturn(5L);
            Instant slot = validFutureSlot();

            GuardRailResult result = guardRailService.validate(institutionId, slot);

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.getSoftWarnings()).isNotEmpty();
        }
    }

    // ── GuardRailResult convenience methods ───────────────────────────────────
    @Nested
    @DisplayName("GuardRailResult convenience methods")
    class GuardRailResultTests {

        @Test
        @DisplayName("isBlocked returns true only when hardBlocks is non-empty")
        void isBlocked_returnsTrueOnlyWhenHardBlocksPresent() {
            when(slotReservationRepository.existsActiveWithin30Minutes(any(), any())).thenReturn(true);

            GuardRailResult result = guardRailService.validate(institutionId, validFutureSlot());

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.isClean()).isFalse();
        }

        @Test
        @DisplayName("hasWarnings returns true only when softWarnings present and no hard blocks")
        void hasWarnings_returnsTrueOnlyWhenSoftWarningsPresentWithNoHardBlocks() {
            when(submissionRepository.countUnpublishedByInstitution(any())).thenReturn(5L);

            GuardRailResult result = guardRailService.validate(institutionId, validFutureSlot());

            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.isBlocked()).isFalse();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Instant validFutureSlot() {
        return Instant.now().plus(3, ChronoUnit.HOURS);
    }
}
