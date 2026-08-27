package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.ScheduledJobRun;
import com.dasigconnect.backend.repository.ScheduledJobRunRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledJobHealthService {

    private final ScheduledJobRunRepository scheduledJobRunRepository;

    public ScheduledJobHealthService(ScheduledJobRunRepository scheduledJobRunRepository) {
        this.scheduledJobRunRepository = scheduledJobRunRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String jobName, Instant startedAt) {
        saveRun(jobName, "SUCCESS", startedAt, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String jobName, Instant startedAt, Exception exception) {
        saveRun(jobName, "FAILED", startedAt, summarize(exception));
    }

    private void saveRun(String jobName, String status, Instant startedAt, String errorMessage) {
        Instant completedAt = Instant.now();
        ScheduledJobRun run = new ScheduledJobRun();
        run.setJobName(jobName);
        run.setStatus(status);
        run.setStartedAt(startedAt);
        run.setCompletedAt(completedAt);
        run.setDurationMs(Duration.between(startedAt, completedAt).toMillis());
        run.setErrorMessage(errorMessage);
        scheduledJobRunRepository.save(run);
    }

    private static String summarize(Exception exception) {
        if (exception == null) {
            return "Unknown failure";
        }
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= 1000 ? summary : summary.substring(0, 1000);
    }
}
