package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.media.RepositoryHealthDto;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.RepositoryHealthCounts;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RepositoryHealthServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    private RepositoryHealthService service;

    @BeforeEach
    void setUp() {
        service = new RepositoryHealthService(mediaAssetRepository, 30);
    }

    @Test
    void admin_withoutInstitution_aggregatesNetworkWide() {
        when(mediaAssetRepository.aggregateRepositoryHealth(isNull(), any(Instant.class)))
                .thenReturn(counts());

        RepositoryHealthDto dto = service.health(null, admin());

        assertThat(dto.scope()).isEqualTo("network");
        assertThat(dto.institutionId()).isNull();
        assertThat(dto.totals().activeAssets()).isEqualTo(100);
        // 80 covered / 100 active = 80.0%; 30 curated / 60 ready = 50.0%; 2+1+1 failures = 4
        assertThat(dto.integrity().checksumCoveragePct()).isEqualTo(80.0);
        assertThat(dto.integrity().failuresTotal()).isEqualTo(4);
        assertThat(dto.curation().completenessPct()).isEqualTo(50.0);
        assertThat(dto.curation().uncurated()).isEqualTo(30);
    }

    @Test
    void admin_withInstitution_scopesToThatInstitution() {
        UUID institutionId = UUID.randomUUID();
        when(mediaAssetRepository.aggregateRepositoryHealth(eq(institutionId), any(Instant.class)))
                .thenReturn(counts());

        RepositoryHealthDto dto = service.health(institutionId, admin());

        assertThat(dto.scope()).isEqualTo("institution");
        assertThat(dto.institutionId()).isEqualTo(institutionId);
    }

    @Test
    void validator_isScopedToOwnInstitution() {
        UUID institutionId = UUID.randomUUID();
        JwtUserDetails validator =
                new JwtUserDetails(UUID.randomUUID(), "v@example.com", "validator", institutionId);
        when(mediaAssetRepository.aggregateRepositoryHealth(eq(institutionId), any(Instant.class)))
                .thenReturn(counts());

        RepositoryHealthDto dto = service.health(null, validator);

        assertThat(dto.scope()).isEqualTo("institution");
        assertThat(dto.institutionId()).isEqualTo(institutionId);
    }

    @Test
    void validator_requestingAnotherInstitution_isForbidden() {
        JwtUserDetails validator =
                new JwtUserDetails(UUID.randomUUID(), "v@example.com", "validator", UUID.randomUUID());

        assertThatThrownBy(() -> service.health(UUID.randomUUID(), validator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        verify(mediaAssetRepository, never()).aggregateRepositoryHealth(any(), any());
    }

    @Test
    void repeatedCall_withinTtl_usesCachedSnapshot() {
        UUID institutionId = UUID.randomUUID();
        when(mediaAssetRepository.aggregateRepositoryHealth(eq(institutionId), any(Instant.class)))
                .thenReturn(counts());

        service.health(institutionId, admin());
        service.health(institutionId, admin());

        verify(mediaAssetRepository, times(1)).aggregateRepositoryHealth(eq(institutionId), any(Instant.class));
    }

    private static JwtUserDetails admin() {
        return new JwtUserDetails(UUID.randomUUID(), "admin@example.com", "administrator", null);
    }

    /** active=100, ready=60, covered=80, verified=76, pending=20, 2 mismatch/1 missing/1 error, curated=30. */
    private static RepositoryHealthCounts counts() {
        return new RepositoryHealthCounts() {
            public long getActiveAssets() { return 100; }
            public long getStorageBytes() { return 5_000_000; }
            public long getReadyAssets() { return 60; }
            public long getChecksumCovered() { return 80; }
            public long getIntegrityVerified() { return 76; }
            public long getIntegrityPending() { return 20; }
            public long getIntegrityMismatch() { return 2; }
            public long getIntegrityMissing() { return 1; }
            public long getIntegrityError() { return 1; }
            public long getReviewOpen() { return 3; }
            public long getReviewAcknowledged() { return 1; }
            public long getProcessingFailed() { return 2; }
            public long getProcessingPending() { return 5; }
            public long getMetadataCurated() { return 30; }
            public long getUnorganized() { return 12; }
            public long getInternalOnly() { return 40; }
            public long getDuplicateCandidates() { return 6; }
            public long getDeletedPendingPurge() { return 8; }
            public long getDeletedApproachingPurge() { return 3; }
        };
    }
}
