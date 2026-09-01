package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.FacebookEngagementAnalyticsClient.EngagementSample;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EngagementRecommendationServiceTest {

    @Mock private FacebookEngagementAnalyticsClient analyticsClient;
    @Mock private GuardRailService guardRailService;
    private EngagementRecommendationService service;
    private JwtUserDetails user;

    @BeforeEach
    void setUp() {
        service = new EngagementRecommendationService(analyticsClient, guardRailService);
        user = new JwtUserDetails(UUID.randomUUID(), "contributor@example.com", "contributor", UUID.randomUUID());
        lenient().when(guardRailService.validate(any(), any())).thenReturn(new GuardRailResult());
    }

    @Test
    void insufficientHistoryReturnsDefaultSlots() throws Exception {
        when(analyticsClient.fetchRecentPostEngagement()).thenReturn(List.of(
                new EngagementSample(Instant.parse("2026-08-04T10:00:00Z"), 12)));

        var result = service.recommend(user, null);

        assertThat(result.available()).isTrue();
        assertThat(result.source()).isEqualTo("DEFAULT");
        assertThat(result.notice()).contains("improve");
        assertThat(result.slots()).isNotEmpty();
        assertThat(result.slots()).allMatch(slot -> slot.scheduledAt().isAfter(Instant.now()));
    }

    @Test
    void analyticsFailureHidesRecommendations() throws Exception {
        when(analyticsClient.fetchRecentPostEngagement()).thenThrow(new IOException("offline"));

        var result = service.recommend(user, null);

        assertThat(result.available()).isFalse();
        assertThat(result.source()).isEqualTo("UNAVAILABLE");
        assertThat(result.slots()).isEmpty();
    }

    @Test
    void sufficientHistoryUsesFacebookEngagementWindows() throws Exception {
        List<EngagementSample> samples = IntStream.range(0, 20)
                .mapToObj(index -> new EngagementSample(
                        Instant.parse("2026-08-04T10:00:00Z").minusSeconds(index * 7L * 24 * 3600),
                        25 + index))
                .toList();
        when(analyticsClient.fetchRecentPostEngagement()).thenReturn(samples);

        var result = service.recommend(user, null);

        assertThat(result.available()).isTrue();
        assertThat(result.source()).isEqualTo("HISTORICAL");
        assertThat(result.sampleSize()).isEqualTo(20);
        assertThat(result.slots()).isNotEmpty();
    }

    @Test
    void hardBlockedCandidatesAreExcluded() throws Exception {
        when(analyticsClient.fetchRecentPostEngagement()).thenReturn(List.of());
        when(guardRailService.validate(any(), any())).thenReturn(
                new GuardRailResult(List.of(new com.dasigconnect.backend.model.dto.guardrail.GuardRailViolation(
                        "GR-H1", "Conflict")), List.of()));

        var result = service.recommend(user, null);

        assertThat(result.available()).isTrue();
        assertThat(result.slots()).isEmpty();
    }
}
