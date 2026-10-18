package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.TaskCompletionLog;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.TaskCompletionLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.TaskTelemetryService.NextActionDto;
import com.dasigconnect.backend.service.TaskTelemetryService.RoleTaskSummaryDto;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskCompleteRequest;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskEventRequest;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskStartRequest;
import com.dasigconnect.backend.service.TaskTelemetryService.TaskTelemetryDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskTelemetryServiceTest {

    @Mock
    private TaskCompletionLogRepository repository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private TaskTelemetryService taskTelemetryService;

    private UUID userId;
    private UUID institutionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        institutionId = UUID.randomUUID();
    }

    @Test
    void getNextAction_contributorWithRevision_prioritizesNeedsRevision() {
        JwtUserDetails contributor = new JwtUserDetails(userId, "contrib@hei.edu.ph", "contributor", institutionId);
        Submission rev = new Submission();
        rev.setId(UUID.randomUUID());
        rev.setStatus(SubmissionStatus.needs_revision);

        when(submissionRepository.findByContributorIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(rev));

        NextActionDto action = taskTelemetryService.getNextAction(contributor);

        assertThat(action.kind()).isEqualTo("needs_revision");
        assertThat(action.label()).contains("Needs revision — the Moderator asked for changes");
        assertThat(action.deepLink()).isEqualTo("/submissions/new?id=" + rev.getId());
    }

    @Test
    void getNextAction_moderatorWithPendingQueue_returnsPendingReviewAction() {
        JwtUserDetails moderator = new JwtUserDetails(userId, "mod@dasig.org", "moderator", null);
        Submission pending = new Submission();
        pending.setId(UUID.randomUUID());
        pending.setStatus(SubmissionStatus.pending);

        when(submissionRepository.findValidationQueue(any(Instant.class)))
                .thenReturn(List.of(pending));

        NextActionDto action = taskTelemetryService.getNextAction(moderator);

        assertThat(action.kind()).isEqualTo("pending_review");
        assertThat(action.count()).isEqualTo(1L);
        assertThat(action.label()).isEqualTo("1 submission awaiting review — Review next");
        assertThat(action.deepLink()).isEqualTo("/queue?submission=" + pending.getId());
    }

    @Test
    void getNextAction_adminWithPublishFailed_prioritizesFailedPublications() {
        JwtUserDetails admin = new JwtUserDetails(userId, "admin@dasig.org", "admin", null);
        SubmissionRepository.SubmissionStatusCount failedRow = new SubmissionRepository.SubmissionStatusCount() {
            @Override
            public SubmissionStatus getStatus() {
                return SubmissionStatus.publish_failed;
            }

            @Override
            public long getCount() {
                return 2L;
            }
        };

        when(submissionRepository.countValidationStatuses()).thenReturn(List.of(failedRow));

        NextActionDto action = taskTelemetryService.getNextAction(admin);

        assertThat(action.kind()).isEqualTo("publish_failed");
        assertThat(action.count()).isEqualTo(2L);
        assertThat(action.label()).isEqualTo("2 failed publications need action");
        assertThat(action.deepLink()).isEqualTo("/queue?tab=failed");
    }

    @Test
    void startAndCompleteTask_recordsFirstAttemptSuccessAndDuration() {
        JwtUserDetails contributor = new JwtUserDetails(userId, "contrib@hei.edu.ph", "contributor", institutionId);
        UUID submissionId = UUID.randomUUID();

        when(repository.findFirstByUserIdAndTaskNameAndStatusOrderByStartedAtDesc(
                eq(userId), eq("CREATE_AND_SUBMIT_POST"), eq("STARTED")))
                .thenReturn(Optional.empty());
        when(repository.countByUserIdAndTaskName(userId, "CREATE_AND_SUBMIT_POST")).thenReturn(0L);
        when(repository.save(any(TaskCompletionLog.class))).thenAnswer(inv -> {
            TaskCompletionLog saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        TaskTelemetryDto started = taskTelemetryService.startTask(
                contributor, new TaskStartRequest("CREATE_AND_SUBMIT_POST", submissionId));
        assertThat(started.status()).isEqualTo("STARTED");
        assertThat(started.attemptNumber()).isEqualTo(1);
        assertThat(started.firstAttemptSuccess()).isTrue();

        TaskCompletionLog activeLog = new TaskCompletionLog();
        activeLog.setId(started.id());
        activeLog.setUserId(userId);
        activeLog.setUserRole("contributor");
        activeLog.setTaskName("CREATE_AND_SUBMIT_POST");
        activeLog.setStartedAt(Instant.now().minusSeconds(45));
        activeLog.setAttemptNumber(1);
        activeLog.setStatus("STARTED");

        when(repository.findFirstByUserIdAndTaskNameAndStatusOrderByStartedAtDesc(
                eq(userId), eq("CREATE_AND_SUBMIT_POST"), eq("STARTED")))
                .thenReturn(Optional.of(activeLog));

        TaskTelemetryDto completed = taskTelemetryService.completeTask(
                contributor, new TaskCompleteRequest("CREATE_AND_SUBMIT_POST", submissionId, 45000L, 0, true));

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.durationMs()).isEqualTo(45000L);
        assertThat(completed.firstAttemptSuccess()).isTrue();
    }

    @Test
    void recordTaskEvent_dispatchesStartedAndCompletedEvents() {
        JwtUserDetails moderator = new JwtUserDetails(userId, "mod@dasig.org", "moderator", null);
        UUID submissionId = UUID.randomUUID();

        when(repository.findFirstByUserIdAndTaskNameAndStatusOrderByStartedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.countByUserIdAndTaskName(any(), any())).thenReturn(0L);
        when(repository.save(any(TaskCompletionLog.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskTelemetryDto dto = taskTelemetryService.recordTaskEvent(
                moderator, new TaskEventRequest("REVIEW_SUBMISSION", submissionId, "completed", "sess-1"));

        assertThat(dto.status()).isEqualTo("COMPLETED");
        verify(repository).save(any(TaskCompletionLog.class));
    }

    @Test
    void getSummary_computesFirstAttemptRateAndMedianSecondsPerRole() {
        TaskCompletionLog log1 = new TaskCompletionLog();
        log1.setUserRole("contributor");
        log1.setFirstAttemptSuccess(true);
        log1.setDurationMs(30000L);
        log1.setStatus("COMPLETED");

        TaskCompletionLog log2 = new TaskCompletionLog();
        log2.setUserRole("contributor");
        log2.setFirstAttemptSuccess(false);
        log2.setDurationMs(50000L);
        log2.setStatus("COMPLETED");

        when(repository.findByStatusOrderByCompletedAtDesc("COMPLETED")).thenReturn(List.of(log1, log2));

        List<RoleTaskSummaryDto> summary = taskTelemetryService.getSummary();

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).userRole()).isEqualTo("contributor");
        assertThat(summary.get(0).completedTasks()).isEqualTo(2L);
        assertThat(summary.get(0).firstAttemptSuccessCount()).isEqualTo(1L);
        assertThat(summary.get(0).firstAttemptCompletionRatePercent()).isEqualTo(50.0);
        assertThat(summary.get(0).medianDurationSeconds()).isEqualTo(40.0);
    }
}

