package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.systemhealth.MediaEmbeddingCoverageDto;
import com.dasigconnect.backend.external.VoyageAIClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class MediaAiTelemetryServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private VoyageAIClient voyageAIClient;

    private final PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    };

    @Test
    void record_clampsCountersAndPersistsContentFreeMeasurement() {
        MediaAiTelemetryService service = new MediaAiTelemetryService(
                jdbcTemplate, transactionManager, voyageAIClient);
        UUID assetId = UUID.randomUUID();

        service.record("VOYAGE_IMAGE_EMBEDDING", -20, "SUCCESS", assetId,
                null, 0, -1, -1);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void record_databaseFailureDoesNotEscapeIntoMediaPipeline() {
        MediaAiTelemetryService service = new MediaAiTelemetryService(
                jdbcTemplate, transactionManager, voyageAIClient);
        doThrow(new RuntimeException("metrics unavailable"))
                .when(jdbcTemplate).update(anyString(), any(Object[].class));

        assertThatCode(() -> service.record("QUEUE_DELAY", 10, "SUCCESS",
                UUID.randomUUID(), null, 1, 0, 0)).doesNotThrowAnyException();
    }

    @Test
    void recordRankingShadow_persistsOnlyIdentifiersAndAggregateCounts() {
        MediaAiTelemetryService service = new MediaAiTelemetryService(
                jdbcTemplate, transactionManager, voyageAIClient);

        service.recordRankingShadow(
                UUID.randomUUID(), UUID.randomUUID(), 8, 7, 5, true);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void operationalMetrics_databaseFailuresReturnUnavailableMetrics() {
        MediaAiTelemetryService service = new MediaAiTelemetryService(
                jdbcTemplate, transactionManager, voyageAIClient);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("monitoring unavailable"));

        var metrics = service.operationalMetrics(30);

        assertThat(metrics).hasSize(8);
        assertThat(metrics).allSatisfy(metric ->
                assertThat(metric.status().name()).isEqualTo("UNAVAILABLE"));
    }

    @Test
    void embeddingCoverage_groupsEligibleImagesWithoutExposingContent() {
        MediaAiTelemetryService service = new MediaAiTelemetryService(
                jdbcTemplate, transactionManager, voyageAIClient);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers
                .<RowMapper<MediaEmbeddingCoverageDto>>any(), any(Object[].class))).thenReturn(List.of());

        assertThat(service.embeddingCoverage()).isEmpty();

        verify(jdbcTemplate).query(anyString(), org.mockito.ArgumentMatchers
                .<RowMapper<MediaEmbeddingCoverageDto>>any(), any(Object[].class));
    }
}
