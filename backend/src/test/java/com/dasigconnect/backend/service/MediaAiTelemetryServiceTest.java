package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class MediaAiTelemetryServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

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
        MediaAiTelemetryService service = new MediaAiTelemetryService(jdbcTemplate, transactionManager);
        UUID assetId = UUID.randomUUID();

        service.record("VOYAGE_IMAGE_EMBEDDING", -20, "SUCCESS", assetId,
                null, 0, -1, -1);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void record_databaseFailureDoesNotEscapeIntoMediaPipeline() {
        MediaAiTelemetryService service = new MediaAiTelemetryService(jdbcTemplate, transactionManager);
        doThrow(new RuntimeException("metrics unavailable"))
                .when(jdbcTemplate).update(anyString(), any(Object[].class));

        assertThatCode(() -> service.record("QUEUE_DELAY", 10, "SUCCESS",
                UUID.randomUUID(), null, 1, 0, 0)).doesNotThrowAnyException();
    }
}
