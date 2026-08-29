package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.audit.AuditEntityType;
import com.dasigconnect.backend.model.dto.audit.AuditLogCategory;
import com.dasigconnect.backend.model.dto.audit.AuditLogDto;
import com.dasigconnect.backend.model.dto.audit.AuditLogFilterCriteria;
import com.dasigconnect.backend.model.entity.AuditLog;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.UserRole;
import com.dasigconnect.backend.repository.AuditLogRepository;
import com.dasigconnect.backend.repository.FacebookPageTokenRepository;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogWriter auditLogWriter;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private InstitutionRepository institutionRepository;

    @Mock
    private FacebookPageTokenRepository facebookPageTokenRepository;

    private ObjectMapper objectMapper;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditLogService = new AuditLogService(
                auditLogRepository,
                auditLogWriter,
                submissionRepository,
                userRepository,
                mediaAssetRepository,
                institutionRepository,
                facebookPageTokenRepository,
                objectMapper
        );
    }

    @Test
    void searchAuditLogs_mapsEntitiesAndDiffsCorrectly() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setEventTitle("DOST Science Week");
        when(submissionRepository.findAllById(any())).thenReturn(List.of(submission));

        User actor = new User();
        actor.setId(UUID.randomUUID());
        actor.setEmail("validator@dasig.gov.ph");
        actor.setFirstName("Maria");
        actor.setLastName("Clara");
        actor.setRole(UserRole.moderator);
        when(userRepository.findAllByIdWithInstitution(any())).thenReturn(List.of(actor));

        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActor(actor);
        log.setAction("SUBMISSION_EDITED_AND_APPROVED");
        log.setResourceId(submissionId);
        log.setIpAddress("127.0.0.1");
        log.setMetadata("{\"diff\":{\"caption\":{\"from\":\"Old Caption\",\"to\":\"New Updated Caption\"}},\"remarks\":\"Fixed typo\"}");

        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        AuditLogFilterCriteria criteria = new AuditLogFilterCriteria(
                null, null, null, null, AuditLogCategory.EDIT_AND_REVISION, null, AuditEntityType.SUBMISSION, null, null
        );

        Page<AuditLogDto> result = auditLogService.searchAuditLogs(criteria, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        AuditLogDto dto = result.getContent().get(0);
        assertThat(dto.action()).isEqualTo("SUBMISSION_EDITED_AND_APPROVED");
        assertThat(dto.category()).isEqualTo(AuditLogCategory.EDIT_AND_REVISION);
        assertThat(dto.actor().name()).isEqualTo("Maria Clara");
        assertThat(dto.entity().label()).isEqualTo("DOST Science Week");
        assertThat(dto.entity().exists()).isTrue();
        assertThat(dto.diffs()).hasSize(1);
        assertThat(dto.diffs().get(0).field()).isEqualTo("caption");
        assertThat(dto.diffs().get(0).fromValue()).isEqualTo("Old Caption");
        assertThat(dto.diffs().get(0).toValue()).isEqualTo("New Updated Caption");
    }

    @Test
    void searchAuditLogs_whenEntityDeleted_returnsFallbackLabel() {
        UUID missingId = UUID.randomUUID();
        when(submissionRepository.findAllById(any())).thenReturn(List.of());

        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction("SUBMISSION_DELETED");
        log.setResourceId(missingId);

        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogDto> result = auditLogService.searchAuditLogs(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        AuditLogDto dto = result.getContent().get(0);
        assertThat(dto.entity().exists()).isFalse();
        assertThat(dto.entity().label()).isEqualTo("[Entity no longer available]");
        assertThat(dto.entity().jumpUrl()).isNull();
    }

    @Test
    void exportAuditLogsCsv_formatsCsvCorrectly() {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction("TOKEN_REAUTHORIZED");
        log.setIpAddress("10.0.0.1");

        when(auditLogRepository.findAll(any(Specification.class))).thenReturn(List.of(log));

        String csv = auditLogService.exportAuditLogsCsv(null);

        assertThat(csv).contains("Log ID,Timestamp (PHT),Actor Name,Actor Email");
        assertThat(csv).contains("TOKEN_REAUTHORIZED");
        assertThat(csv).contains("10.0.0.1");
    }
}
