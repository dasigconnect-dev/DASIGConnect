package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.model.dto.analytics.InstitutionPostsDto;
import com.dasigconnect.backend.repository.AnalyticsRepository;
import com.dasigconnect.backend.repository.AnalyticsRepository.AiStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.AnalyticsScope;
import com.dasigconnect.backend.repository.AnalyticsRepository.CompletenessStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.OperationalStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PostingDelayStats;
import com.dasigconnect.backend.repository.AnalyticsRepository.PublishedPostStats;
import com.dasigconnect.backend.security.JwtUserDetails;

@ExtendWith(MockitoExtension.class)
class MetricsAggregatorServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    private MetricsAggregatorService service;

    @BeforeEach
    void setUp() {
        service = new MetricsAggregatorService(analyticsRepository);
    }

    @Test
    void summary_aggregatesKpisAndScopesValidatorToInstitution() {
        UUID institutionId = UUID.randomUUID();
        JwtUserDetails validator = new JwtUserDetails(UUID.randomUUID(), "validator@test.local", "validator", institutionId);

        when(analyticsRepository.averagePostingDelay(any(), any(), any()))
                .thenReturn(new PostingDelayStats(2.345, 6));
        when(analyticsRepository.contentCompleteness(any(), any(), any()))
                .thenReturn(new CompletenessStats(19, 20));
        when(analyticsRepository.publishedPostStats(any(), any(), any()))
                .thenReturn(new PublishedPostStats(4, 3, 1, 0));
        when(analyticsRepository.postsByInstitution(any(), any(), any()))
                .thenReturn(List.of(new InstitutionPostsDto(institutionId, "CIT-U", 4, 3, 1, 0)));
        when(analyticsRepository.aiPerformance(any(), any(), any()))
                .thenReturn(new AiStats(10, 7, 4, 1, 8, 6));
        when(analyticsRepository.operationalHealth(any(), any(), any(), any()))
                .thenReturn(new OperationalStats(12, 1, 2, 5, 4, 3, 9));

        var summary = service.summary("30d", validator);

        assertThat(summary.averagePostingDelay().value()).isEqualTo(2.35);
        assertThat(summary.contentCompleteness().value()).isEqualTo(95.0);
        assertThat(summary.contentCompleteness().targetMet()).isTrue();
        assertThat(summary.totalPostsPublished().value()).isEqualTo(4);
        assertThat(summary.aiPerformance().captionAcceptanceRate()).isEqualTo(70.0);
        assertThat(summary.aiPerformance().insufficientData()).isFalse();
        assertThat(summary.operationalHealth().publishingSuccessRate()).isEqualTo(80.0);

        ArgumentCaptor<AnalyticsScope> scopeCaptor = ArgumentCaptor.forClass(AnalyticsScope.class);
        org.mockito.Mockito.verify(analyticsRepository)
                .averagePostingDelay(any(Instant.class), any(Instant.class), scopeCaptor.capture());
        assertThat(scopeCaptor.getValue().role()).isEqualTo("validator");
        assertThat(scopeCaptor.getValue().institutionId()).isEqualTo(institutionId);
        assertThat(scopeCaptor.getValue().userId()).isNull();
    }

    @Test
    void export_returnsCsvWithHeaders() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "administrator", null);
        when(analyticsRepository.exportRows(any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("metric", "publication_attempts", "value", 5)));

        var export = service.export("operational-health", "7d", admin);

        assertThat(export.filename()).contains("operational-health").endsWith(".csv");
        assertThat(export.content()).contains("\"metric\",\"value\"");
        assertThat(export.content()).contains("\"publication_attempts\",\"5\"");
    }

    @Test
    void summary_rejectsUnsupportedRange() {
        JwtUserDetails admin = new JwtUserDetails(UUID.randomUUID(), "admin@test.local", "administrator", null);

        assertThatThrownBy(() -> service.summary("13d", admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported analytics range");
    }
}
