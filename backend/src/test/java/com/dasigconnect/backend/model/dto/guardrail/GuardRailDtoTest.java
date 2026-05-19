package com.dasigconnect.backend.model.dto.guardrail;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GuardRailDtoTest {

    @Nested
    @DisplayName("GuardRailResult")
    class GuardRailResultTests {

        @Test
        @DisplayName("isClean() returns true when no blocks or warnings")
        void isClean_whenEmpty() {
            GuardRailResult result = new GuardRailResult();
            assertThat(result.isClean()).isTrue();
            assertThat(result.isBlocked()).isFalse();
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("isBlocked() returns true when hardBlocks is non-empty")
        void isBlocked_whenHardBlocksPresent() {
            GuardRailViolation block = new GuardRailViolation("GR-H1", "Conflict");
            GuardRailResult result = new GuardRailResult(List.of(block), List.of());

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.isClean()).isFalse();
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("hasWarnings() returns true only when soft warnings present and no hard blocks")
        void hasWarnings_whenOnlySoftWarningsPresent() {
            GuardRailViolation warning = new GuardRailViolation("GR-S1", "3 posts queued");
            GuardRailResult result = new GuardRailResult(List.of(), List.of(warning));

            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.isBlocked()).isFalse();
            assertThat(result.isClean()).isFalse();
        }

        @Test
        @DisplayName("hasWarnings() returns false when both blocks and warnings are present")
        void hasWarnings_returnsFalse_whenBlockedAndHasWarnings() {
            GuardRailViolation block = new GuardRailViolation("GR-H1", "Conflict");
            GuardRailViolation warning = new GuardRailViolation("GR-S1", "3 posts queued");
            GuardRailResult result = new GuardRailResult(List.of(block), List.of(warning));

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("null lists are treated as empty")
        void nullLists_treatedAsEmpty() {
            GuardRailResult result = new GuardRailResult(null, null);

            assertThat(result.getHardBlocks()).isEmpty();
            assertThat(result.getSoftWarnings()).isEmpty();
            assertThat(result.isClean()).isTrue();
        }

        @Test
        @DisplayName("setters replace lists correctly")
        void setters_replaceListsCorrectly() {
            GuardRailResult result = new GuardRailResult();
            GuardRailViolation v = new GuardRailViolation("GR-H2", "Too soon");

            result.setHardBlocks(List.of(v));
            assertThat(result.isBlocked()).isTrue();

            result.setHardBlocks(Collections.emptyList());
            assertThat(result.isBlocked()).isFalse();
        }
    }

    @Nested
    @DisplayName("GuardRailViolation")
    class GuardRailViolationTests {

        @Test
        @DisplayName("two-arg constructor sets empty suggestedSlots")
        void twoArgConstructor_setsEmptySuggestedSlots() {
            GuardRailViolation v = new GuardRailViolation("GR-H2", "Too soon");

            assertThat(v.getCode()).isEqualTo("GR-H2");
            assertThat(v.getMessage()).isEqualTo("Too soon");
            assertThat(v.getSuggestedSlots()).isEmpty();
        }

        @Test
        @DisplayName("three-arg constructor sets suggestedSlots correctly")
        void threeArgConstructor_setsSuggestedSlots() {
            Instant suggestion = Instant.now().plus(4, ChronoUnit.HOURS);
            GuardRailViolation v = new GuardRailViolation("GR-H1", "Conflict", List.of(suggestion));

            assertThat(v.getSuggestedSlots()).containsExactly(suggestion);
        }

        @Test
        @DisplayName("null suggestedSlots is treated as empty list")
        void nullSuggestedSlots_treatedAsEmpty() {
            GuardRailViolation v = new GuardRailViolation("GR-H1", "Conflict", null);

            assertThat(v.getSuggestedSlots()).isEmpty();
        }

        @Test
        @DisplayName("setSuggestedSlots with null sets empty list")
        void setSuggestedSlots_withNull_setsEmpty() {
            GuardRailViolation v = new GuardRailViolation("GR-H1", "Conflict");
            v.setSuggestedSlots(null);

            assertThat(v.getSuggestedSlots()).isEmpty();
        }
    }
}
