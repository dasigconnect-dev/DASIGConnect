package com.dasigconnect.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.TaskCompletionLog;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.TaskCompletionLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@Service
public class TaskTelemetryService {

    private final TaskCompletionLogRepository repository;
    private final SubmissionRepository submissionRepository;

    public TaskTelemetryService(
            TaskCompletionLogRepository repository,
            SubmissionRepository submissionRepository) {
        this.repository = repository;
        this.submissionRepository = submissionRepository;
    }

    public record TaskStartRequest(String taskName, UUID targetEntityId) {}

    public record TaskCompleteRequest(
            String taskName,
            UUID targetEntityId,
            Long durationMs,
            Integer errorCount,
            Boolean firstAttemptSuccess) {}

    public record TaskEventRequest(
            String taskType,
            UUID submissionId,
            String event,
            String clientSessionId) {}

    public record NextActionDto(
            String kind,
            String label,
            long count,
            String deepLink) {}

    public record TaskTelemetryDto(
            UUID id,
            String userRole,
            String taskName,
            UUID targetEntityId,
            Instant startedAt,
            Instant completedAt,
            Long durationMs,
            int attemptNumber,
            boolean firstAttemptSuccess,
            int errorCount,
            String status) {
        public static TaskTelemetryDto from(TaskCompletionLog entity) {
            return new TaskTelemetryDto(
                    entity.getId(),
                    entity.getUserRole(),
                    entity.getTaskName(),
                    entity.getTargetEntityId(),
                    entity.getStartedAt(),
                    entity.getCompletedAt(),
                    entity.getDurationMs(),
                    entity.getAttemptNumber(),
                    entity.isFirstAttemptSuccess(),
                    entity.getErrorCount(),
                    entity.getStatus());
        }
    }

    public record RoleTaskSummaryDto(
            String userRole,
            long completedTasks,
            long firstAttemptSuccessCount,
            double firstAttemptCompletionRatePercent,
            double medianDurationSeconds) {}

    @Transactional
    public TaskTelemetryDto recordTaskEvent(JwtUserDetails user, TaskEventRequest request) {
        String evt = request.event() == null ? "started" : request.event().trim().toLowerCase(Locale.ROOT);
        String taskType = request.taskType() == null ? "CORE_TASK" : request.taskType().trim();
        if ("completed".equals(evt)) {
            return completeTask(user, new TaskCompleteRequest(taskType, request.submissionId(), null, 0, null));
        }
        return startTask(user, new TaskStartRequest(taskType, request.submissionId()));
    }

    @Transactional(readOnly = true)
    public NextActionDto getNextAction(JwtUserDetails user) {
        String role = user.role() == null ? "contributor" : user.role().toLowerCase(Locale.ROOT);
        if ("admin".equals(role) || "administrator".equals(role)) {
            Map<SubmissionStatus, Long> counts = submissionRepository.countValidationStatuses().stream()
                    .collect(Collectors.toMap(
                            SubmissionRepository.SubmissionStatusCount::getStatus,
                            SubmissionRepository.SubmissionStatusCount::getCount,
                            Long::sum));
            long failedCount = counts.getOrDefault(SubmissionStatus.publish_failed, 0L)
                    + counts.getOrDefault(SubmissionStatus.direct_post_failed, 0L);
            if (failedCount > 0) {
                return new NextActionDto(
                        "publish_failed",
                        failedCount + " failed publication" + (failedCount == 1 ? "" : "s") + " need"
                                + (failedCount == 1 ? "s" : "") + " action",
                        failedCount,
                        "/queue?tab=failed");
            }
            long pendingCount = counts.getOrDefault(SubmissionStatus.pending, 0L)
                    + counts.getOrDefault(SubmissionStatus.in_review, 0L);
            return new NextActionDto(
                    "pending_review",
                    pendingCount + " submission" + (pendingCount == 1 ? "" : "s") + " awaiting review — Review next",
                    pendingCount,
                    "/queue");
        }
        if ("moderator".equals(role)) {
            List<Submission> queue = submissionRepository.findValidationQueue(Instant.now().plus(Duration.ofMinutes(30)));
            List<Submission> actionable = queue.stream()
                    .filter(s -> s.getStatus() == SubmissionStatus.pending || s.getStatus() == SubmissionStatus.in_review)
                    .toList();
            long pendingCount = actionable.size();
            String deepLink = actionable.isEmpty()
                    ? "/queue"
                    : "/queue?submission=" + actionable.get(0).getId();
            return new NextActionDto(
                    "pending_review",
                    pendingCount + " submission" + (pendingCount == 1 ? "" : "s") + " awaiting review — Review next",
                    pendingCount,
                    deepLink);
        }
        List<Submission> mySubmissions = submissionRepository.findByContributorIdOrderByCreatedAtDesc(user.userId());
        List<Submission> revisions = mySubmissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.needs_revision)
                .toList();
        if (!revisions.isEmpty()) {
            Submission oldest = revisions.get(revisions.size() - 1);
            return new NextActionDto(
                    "needs_revision",
                    "Needs revision — the Moderator asked for changes. Edit and resubmit.",
                    revisions.size(),
                    "/submissions/new?id=" + oldest.getId());
        }
        List<Submission> drafts = mySubmissions.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.draft)
                .toList();
        if (!drafts.isEmpty()) {
            Submission oldestDraft = drafts.get(drafts.size() - 1);
            return new NextActionDto(
                    "draft",
                    "Finish your draft — complete required items and submit",
                    drafts.size(),
                    "/submissions/new?id=" + oldestDraft.getId());
        }
        return new NextActionDto(
                "create_post",
                "Create a new post — draft your event details, attach media, and pick a schedule.",
                0,
                "/submissions/new");
    }

    @Transactional
    public TaskTelemetryDto startTask(JwtUserDetails user, TaskStartRequest request) {
        String normalizedTask = request.taskName() == null ? "CORE_TASK" : request.taskName().trim().toUpperCase();
        Optional<TaskCompletionLog> active = repository
                .findFirstByUserIdAndTaskNameAndStatusOrderByStartedAtDesc(
                        user.userId(), normalizedTask, "STARTED");
        if (active.isPresent()) {
            return TaskTelemetryDto.from(active.get());
        }
        long priorCount = repository.countByUserIdAndTaskName(user.userId(), normalizedTask);
        TaskCompletionLog log = new TaskCompletionLog();
        log.setUserId(user.userId());
        log.setUserRole(user.role());
        log.setTaskName(normalizedTask);
        log.setTargetEntityId(request.targetEntityId());
        log.setStartedAt(Instant.now());
        log.setAttemptNumber((int) priorCount + 1);
        log.setFirstAttemptSuccess(priorCount == 0);
        log.setStatus("STARTED");
        return TaskTelemetryDto.from(repository.save(log));
    }

    @Transactional
    public TaskTelemetryDto completeTask(JwtUserDetails user, TaskCompleteRequest request) {
        String normalizedTask = request.taskName() == null ? "CORE_TASK" : request.taskName().trim().toUpperCase();
        Instant now = Instant.now();
        Optional<TaskCompletionLog> activeOpt = repository
                .findFirstByUserIdAndTaskNameAndStatusOrderByStartedAtDesc(
                        user.userId(), normalizedTask, "STARTED");
        TaskCompletionLog log;
        if (activeOpt.isPresent()) {
            log = activeOpt.get();
        } else {
            long priorCount = repository.countByUserIdAndTaskName(user.userId(), normalizedTask);
            log = new TaskCompletionLog();
            log.setUserId(user.userId());
            log.setUserRole(user.role());
            log.setTaskName(normalizedTask);
            log.setStartedAt(request.durationMs() != null && request.durationMs() > 0
                    ? now.minusMillis(request.durationMs())
                    : now);
            log.setAttemptNumber((int) priorCount + 1);
        }
        if (request.targetEntityId() != null) {
            log.setTargetEntityId(request.targetEntityId());
        }
        log.setCompletedAt(now);
        long computedMs = request.durationMs() != null && request.durationMs() > 0
                ? request.durationMs()
                : Math.max(0L, Duration.between(log.getStartedAt(), now).toMillis());
        log.setDurationMs(computedMs);
        int errors = request.errorCount() != null ? Math.max(0, request.errorCount()) : 0;
        log.setErrorCount(errors);
        boolean firstAttempt = request.firstAttemptSuccess() != null
                ? request.firstAttemptSuccess()
                : (log.getAttemptNumber() == 1 && errors == 0);
        log.setFirstAttemptSuccess(firstAttempt);
        log.setStatus("COMPLETED");
        return TaskTelemetryDto.from(repository.save(log));
    }

    @Transactional(readOnly = true)
    public List<RoleTaskSummaryDto> getSummary() {
        List<TaskCompletionLog> completed = repository.findByStatusOrderByCompletedAtDesc("COMPLETED");
        Map<String, List<TaskCompletionLog>> byRole = completed.stream()
                .collect(Collectors.groupingBy(TaskCompletionLog::getUserRole));
        return byRole.entrySet().stream()
                .map(entry -> {
                    String role = entry.getKey();
                    List<TaskCompletionLog> logs = entry.getValue();
                    long total = logs.size();
                    long firstAttemptOk = logs.stream().filter(TaskCompletionLog::isFirstAttemptSuccess).count();
                    double rate = total > 0 ? Math.round((firstAttemptOk * 1000.0) / total) / 10.0 : 0.0;
                    List<Long> sortedMs = logs.stream()
                            .map(TaskCompletionLog::getDurationMs)
                            .filter(ms -> ms != null && ms >= 0)
                            .sorted()
                            .toList();
                    double medianSeconds = 0.0;
                    if (!sortedMs.isEmpty()) {
                        int mid = sortedMs.size() / 2;
                        long medianMs = sortedMs.size() % 2 == 1
                                ? sortedMs.get(mid)
                                : (sortedMs.get(mid - 1) + sortedMs.get(mid)) / 2;
                        medianSeconds = Math.round(medianMs / 100.0) / 10.0;
                    }
                    return new RoleTaskSummaryDto(role, total, firstAttemptOk, rate, medianSeconds);
                })
                .toList();
    }
}

