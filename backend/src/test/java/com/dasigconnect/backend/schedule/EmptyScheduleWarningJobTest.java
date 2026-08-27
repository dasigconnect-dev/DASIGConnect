package com.dasigconnect.backend.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import com.dasigconnect.backend.event.EmptyScheduleEvent;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.ContentIdeaSuggestionService;

class EmptyScheduleWarningJobTest {

    private InstitutionRepository institutionRepository;
    private SubmissionRepository submissionRepository;
    private ContentIdeaSuggestionService contentIdeaSuggestionService;
    private ApplicationEventPublisher eventPublisher;
    private EmptyScheduleWarningJob job;

    @BeforeEach
    void setUp() {
        institutionRepository = Mockito.mock(InstitutionRepository.class);
        submissionRepository = Mockito.mock(SubmissionRepository.class);
        contentIdeaSuggestionService = Mockito.mock(ContentIdeaSuggestionService.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

        job = new EmptyScheduleWarningJob(
                institutionRepository,
                submissionRepository,
                contentIdeaSuggestionService,
                eventPublisher);
    }

    @Test
    void scanEmptySchedules_whenZeroScheduledPosts_firesEmptyScheduleEvent() {
        Institution inst = new Institution();
        inst.setId(UUID.randomUUID());
        inst.setName("Silliman University");
        inst.setStatus(InstitutionStatus.active);

        when(institutionRepository.findAllByStatus(InstitutionStatus.active)).thenReturn(List.of(inst));
        when(submissionRepository.countUpcomingScheduledByInstitution(eq(inst.getId()), any(), any())).thenReturn(0L);
        when(contentIdeaSuggestionService.generateSuggestions(inst)).thenReturn(List.of("Suggestion 1", "Suggestion 2"));

        job.scanEmptySchedules();

        verify(eventPublisher).publishEvent(any(EmptyScheduleEvent.class));
    }

    @Test
    void scanEmptySchedules_whenPostsScheduled_doesNotFireEvent() {
        Institution inst = new Institution();
        inst.setId(UUID.randomUUID());
        inst.setName("CIT University");
        inst.setStatus(InstitutionStatus.active);

        when(institutionRepository.findAllByStatus(InstitutionStatus.active)).thenReturn(List.of(inst));
        when(submissionRepository.countUpcomingScheduledByInstitution(eq(inst.getId()), any(), any())).thenReturn(2L);

        job.scanEmptySchedules();

        verify(eventPublisher, never()).publishEvent(any(EmptyScheduleEvent.class));
    }
}
