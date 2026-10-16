package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.dto.ai.ProofreadIssueDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProofreadServiceTest {

    private final ProofreadService service =
            new ProofreadService(Mockito.mock(ClaudeVisionClient.class), new ObjectMapper());

    private static final String TEXT = "We recieve the award on Sept 12 at Cebu.";

    @Test
    void keepsFindingsWhoseExcerptIsInTheText() {
        List<ProofreadIssueDto> issues = service.parseIssues("""
                {"issues": [{"kind": "spelling", "excerpt": "recieve", "suggestion": "received",
                "explanation": "Misspelled."}]}""", TEXT);

        assertThat(issues).containsExactly(
                new ProofreadIssueDto("spelling", "recieve", "received", "Misspelled."));
    }

    @Test
    void dropsHallucinatedNoOpDuplicateAndUnknownFindings() {
        List<ProofreadIssueDto> issues = service.parseIssues("""
                {"issues": [
                  {"kind": "spelling", "excerpt": "not in the text", "suggestion": "x", "explanation": ""},
                  {"kind": "grammar", "excerpt": "at Cebu", "suggestion": "at Cebu", "explanation": ""},
                  {"kind": "style", "excerpt": "award", "suggestion": "prize", "explanation": ""},
                  {"kind": "spelling", "excerpt": "recieve", "suggestion": "received", "explanation": ""},
                  {"kind": "spelling", "excerpt": "recieve", "suggestion": "receive", "explanation": ""}
                ]}""", TEXT);

        assertThat(issues).extracting(ProofreadIssueDto::excerpt).containsExactly("recieve");
    }

    @Test
    void meaningChangesNeverCarryAnAutomaticFix() {
        List<ProofreadIssueDto> issues = service.parseIssues("""
                Here you go: {"issues": [{"kind": "meaning", "excerpt": "Sept 12",
                "suggestion": "Sept 21", "explanation": "The original said Sept 21."}]}""", TEXT);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.kind()).isEqualTo("meaning");
            assertThat(issue.suggestion()).isEmpty();
        });
    }

    @Test
    void capsTheNumberOfFindings() {
        StringBuilder json = new StringBuilder("{\"issues\": [");
        String text = "a b c d e f g h i j k l";
        for (String word : text.split(" ")) {
            json.append("{\"kind\":\"spelling\",\"excerpt\":\"").append(word)
                .append("\",\"suggestion\":\"").append(word).append(word).append("\",\"explanation\":\"\"},");
        }
        json.setLength(json.length() - 1);
        json.append("]}");

        assertThat(service.parseIssues(json.toString(), text)).hasSize(ProofreadService.MAX_ISSUES);
    }

    @Test
    void unreadableResponseIsAnApiError() {
        assertThatThrownBy(() -> service.parseIssues("sorry, I can't", TEXT))
                .isInstanceOf(ClaudeVisionClient.ClaudeApiException.class);
    }
}
