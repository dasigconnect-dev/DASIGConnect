package com.dasigconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.external.ClaudeVisionClient.ClaudeApiException;
import com.dasigconnect.backend.model.dto.ai.TopPostTemplateSuggestionDto;
import com.dasigconnect.backend.repository.SubmissionEngagementMetricRepository;
import com.dasigconnect.backend.service.FacebookEngagementAnalyticsClient.PagePostSample;

class TopPostTemplateServiceTest {

    private static final String VALID_JSON = """
            {"name":"Event Highlight","caption":"[EVENT TITLE]\\n\\n[KEY HIGHLIGHT]\\n\\n#DASIGCentralVisayas",
             "tags":["#DASIG","Events","DOST","Innovation","Extra"],
             "insights":["Top posts open with the event name.","They end with a hashtag block."]}
            """;

    private FacebookEngagementAnalyticsClient facebookClient;
    private SubmissionEngagementMetricRepository engagementRepository;
    private ClaudeVisionClient claudeClient;
    private TopPostTemplateService service;

    @BeforeEach
    void setUp() {
        facebookClient = Mockito.mock(FacebookEngagementAnalyticsClient.class);
        engagementRepository = Mockito.mock(SubmissionEngagementMetricRepository.class);
        claudeClient = Mockito.mock(ClaudeVisionClient.class);
        service = new TopPostTemplateService(facebookClient, engagementRepository, claudeClient);
    }

    @Test
    void refusesWithoutCallingClaude_whenTooFewPostsHaveEngagement() throws Exception {
        List<PagePostSample> posts = new ArrayList<>();
        for (int i = 0; i < TopPostTemplateService.MIN_ENGAGED_POSTS - 1; i++) {
            posts.add(pagePost("Engaged " + i, 2, 0, 0));
        }
        for (int i = 0; i < 20; i++) {
            posts.add(pagePost("Silent " + i, 0, 0, 0));
        }
        when(facebookClient.fetchRecentPostsWithText()).thenReturn(posts);

        TopPostTemplateSuggestionDto result = service.suggest();

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).contains("Only 7 of the last 27 posts");
        assertThat(result.source()).isEqualTo(TopPostTemplateService.SOURCE_FACEBOOK);
        verify(claudeClient, never()).generateText(anyString(), anyString(), anyInt());
    }

    @Test
    void sendsTopPostsByWeightedScoreAndContrast_andReturnsParsedDraft() throws Exception {
        List<PagePostSample> posts = new ArrayList<>();
        // Shares weigh 3x: "Shared" (score 30) must outrank "Liked" (score 20).
        posts.add(pagePost("Liked", 20, 0, 0));
        posts.add(pagePost("Shared", 0, 0, 10));
        for (int i = 0; i < 10; i++) {
            posts.add(pagePost("Filler " + i, 5, 0, 0));
        }
        posts.add(pagePost("Dud", 0, 0, 0));
        when(facebookClient.fetchRecentPostsWithText()).thenReturn(posts);
        when(claudeClient.generateText(anyString(), anyString(), anyInt())).thenReturn(VALID_JSON);

        TopPostTemplateSuggestionDto result = service.suggest();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(claudeClient).generateText(anyString(), prompt.capture(), anyInt());
        assertThat(prompt.getValue().indexOf("Shared")).isLessThan(prompt.getValue().indexOf("Liked"));
        assertThat(prompt.getValue()).contains("TOP POST 1 (reactions 0, comments 0, shares 10)");
        assertThat(prompt.getValue()).contains("LOW POST").contains("Dud");

        assertThat(result.available()).isTrue();
        assertThat(result.name()).isEqualTo("Event Highlight");
        assertThat(result.caption()).startsWith("[EVENT TITLE]");
        assertThat(result.tags()).containsExactly("DASIG", "Events", "DOST", "Innovation");
        assertThat(result.insights()).hasSize(2);
        assertThat(result.topPosts()).hasSize(TopPostTemplateService.TOP_POSTS);
        assertThat(result.topPosts().get(0).excerpt()).isEqualTo("Shared");
        assertThat(result.postsConsidered()).isEqualTo(13);
    }

    @Test
    void fallsBackToDasigConnectData_whenFacebookIsUnavailable() throws Exception {
        when(facebookClient.fetchRecentPostsWithText()).thenThrow(new IOException("not configured"));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            rows.add(new Object[] {"Caption " + i, Instant.now(), 3L, 1L, null});
        }
        when(engagementRepository.findPublishedCaptionsWithEngagement(any())).thenReturn(rows);
        when(claudeClient.generateText(anyString(), anyString(), anyInt())).thenReturn(VALID_JSON);

        TopPostTemplateSuggestionDto result = service.suggest();

        assertThat(result.available()).isTrue();
        assertThat(result.source()).isEqualTo(TopPostTemplateService.SOURCE_DASIGCONNECT);
        assertThat(result.topPosts().get(0).shares()).isZero();
    }

    @Test
    void parseDraft_toleratesCodeFencesAndDefaultsMissingName() {
        var draft = service.parseDraft("Here you go:\n```json\n{\"caption\":\"[EVENT TITLE]\"}\n```");

        assertThat(draft.name()).isEqualTo("Top Posts Template");
        assertThat(draft.caption()).isEqualTo("[EVENT TITLE]");
        assertThat(draft.tags()).isEmpty();
    }

    @Test
    void parseDraft_rejectsUnreadableOrEmptyOutput() {
        assertThatThrownBy(() -> service.parseDraft("I can't help with that."))
                .isInstanceOf(ClaudeApiException.class);
        assertThatThrownBy(() -> service.parseDraft("{\"name\":\"x\",\"caption\":\"  \"}"))
                .isInstanceOf(ClaudeApiException.class);
    }

    private static PagePostSample pagePost(String message, long reactions, long comments, long shares) {
        return new PagePostSample(message, Instant.now(), reactions, comments, shares);
    }
}
