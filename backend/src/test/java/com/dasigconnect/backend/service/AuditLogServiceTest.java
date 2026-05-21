package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.AuditLog;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper());
    }

    @Test
    void recordPersistsAuditLogWithSerializedMetadata() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User actor = new User();
        actor.setId(UUID.randomUUID());
        UUID resourceId = UUID.randomUUID();

        AuditLog result = auditLogService.record(
                actor,
                "USER_CREATED",
                "127.0.0.1",
                "JUnit",
                resourceId,
                Map.of("role", "contributor"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(result).isSameAs(saved);
        assertThat(saved.getActor()).isSameAs(actor);
        assertThat(saved.getAction()).isEqualTo("USER_CREATED");
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getUserAgent()).isEqualTo("JUnit");
        assertThat(saved.getResourceId()).isEqualTo(resourceId);
        assertThat(saved.getMetadata()).contains("\"role\":\"contributor\"");
    }

    @Test
    void recordUsesEmptyJsonWhenMetadataIsNullOrEmpty() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog nullMetadata = auditLogService.record(null, "NULL_METADATA", null, null, null, null);
        AuditLog emptyMetadata = auditLogService.record(null, "EMPTY_METADATA", null, null, null, Map.of());

        assertThat(nullMetadata.getMetadata()).isEqualTo("{}");
        assertThat(emptyMetadata.getMetadata()).isEqualTo("{}");
    }

    @Test
    void recordRejectsMetadataThatCannotBeSerialized() {
        Object nonSerializable = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this;
            }
        };

        assertThatThrownBy(() -> auditLogService.record(null, "BAD_METADATA", null, null, null, Map.of("bad", nonSerializable)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Audit metadata");
    }
}
