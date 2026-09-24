package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaAssetStatus;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaContext;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaContextRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SubmissionMediaContextServiceTest {

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final SubmissionMediaAssetRepository media = mock(SubmissionMediaAssetRepository.class);
    private final SubmissionMediaContextRepository contexts = mock(SubmissionMediaContextRepository.class);
    private final SubmissionMediaContextService service = new SubmissionMediaContextService(
            submissions, media, contexts, new ObjectMapper());

    @Test
    void rebuild_aggregatesEveryReadyImageAndReportsIncompleteCounts() throws Exception {
        UUID submissionId = UUID.randomUUID();
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setInstitution(institution);

        MediaAsset first = readyAsset("stage", "presenting", "Hackathon", 0.65);
        MediaAsset second = readyAsset("computer lab", "coding", "Hackathon", 0.88);
        MediaAsset processing = new MediaAsset();
        processing.setId(UUID.randomUUID());
        processing.setStatus(MediaAssetStatus.PROCESSING);
        when(submissions.findById(submissionId)).thenReturn(Optional.of(submission));
        when(media.findMediaAssetsBySubmissionId(submissionId)).thenReturn(List.of(first, second, processing));
        when(contexts.findById(submissionId)).thenReturn(Optional.empty());

        service.rebuild(submissionId);

        ArgumentCaptor<SubmissionMediaContext> captor = ArgumentCaptor.forClass(SubmissionMediaContext.class);
        verify(contexts).save(captor.capture());
        SubmissionMediaContext context = captor.getValue();
        assertThat(context.getReadyAssetCount()).isEqualTo(2);
        assertThat(context.getProcessingAssetCount()).isEqualTo(1);
        assertThat(context.getStatus()).isEqualTo("PARTIAL");
        assertThat(new ObjectMapper().readTree(context.getObservedScenes()).toString())
                .contains("stage", "computer lab");
        assertThat(context.getPeopleCountRanges()).contains("6-20");
        assertThat(context.getEquipmentSignals()).contains("laptop");
        assertThat(context.getRecognitionSignals()).contains("award");
        assertThat(context.getOcrText()).contains("Demo Day");
        assertThat(context.getEventHypotheses()).contains("0.88");
        assertThat(context.getContextText())
                .contains("presenting", "coding", "Hackathon", "6-20", "laptop", "award", "Demo Day");
    }

    private static MediaAsset readyAsset(String scene, String activity, String event, double confidence) {
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setStatus(MediaAssetStatus.READY);
        asset.setAiProcessingVersion("media-ai-v1");
        asset.setObservedScenes(new String[]{scene});
        asset.setObservedActivities(new String[]{activity});
        asset.setVisibleObjects(new String[]{"people"});
        asset.setPeopleCountRange("6-20");
        asset.setEquipmentSignals(new String[]{"laptop"});
        asset.setRecognitionSignals(new String[]{"award"});
        asset.setOcrText(new String[]{"Demo Day"});
        asset.setEventHypotheses("[{\"eventType\":\"" + event + "\",\"confidence\":" + confidence + "}]");
        return asset;
    }
}
