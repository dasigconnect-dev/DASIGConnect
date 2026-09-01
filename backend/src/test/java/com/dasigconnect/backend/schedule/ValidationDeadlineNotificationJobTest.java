package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dasigconnect.backend.repository.NotificationRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.service.EmailDeliveryService;
import com.dasigconnect.backend.service.NotificationService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

class ValidationDeadlineNotificationJobTest {

    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final EmailDeliveryService emailDeliveryService = mock(EmailDeliveryService.class);
    private final ScheduledJobHealthService health = mock(ScheduledJobHealthService.class);

    private final ValidationDeadlineNotificationJob job = new ValidationDeadlineNotificationJob(
            submissionRepository, notificationRepository, userRepository,
            notificationService, emailDeliveryService, health);

    @Test
    void checkValidationDeadlines_noUrgentSubmissions_recordsSuccess() {
        when(submissionRepository.findApproachingDeadlines(any(), any())).thenReturn(List.of());

        job.checkValidationDeadlines();

        verify(notificationService, never()).createNotification(any(), any(), any(), any());
        verify(health).recordSuccess(eq("ValidationDeadlineNotificationJob"), any());
    }

    @Test
    void checkValidationDeadlines_recordsFailureWhenQueryThrows() {
        when(submissionRepository.findApproachingDeadlines(any(), any()))
                .thenThrow(new RuntimeException("db error"));

        job.checkValidationDeadlines();

        verify(health).recordFailure(eq("ValidationDeadlineNotificationJob"), any(), any());
        verify(health, never()).recordSuccess(any(), any());
    }
}
