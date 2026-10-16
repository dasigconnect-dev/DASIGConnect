package com.dasigconnect.backend.service;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.external.ClaudeVisionClient.ClaudeApiException;
import com.dasigconnect.backend.model.dto.ai.ProofreadIssueDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Advisory proofreading for captions (review-queue editor). Suggests fixes —
 * never applies them, never blocks a save. Given the contributor's original
 * too, it reports only what the reviewer's edit introduced, plus meaning drift
 * (a name, date, number, or claim changed).
 */
@Service
public class ProofreadService {

    static final int MAX_ISSUES = 10;
    private static final int MAX_OUTPUT_TOKENS = 1200;
    private static final Set<String> KINDS = Set.of("spelling", "grammar", "clarity", "meaning");

    private final ClaudeVisionClient claudeClient;
    private final ObjectMapper objectMapper;

    public ProofreadService(ClaudeVisionClient claudeClient, ObjectMapper objectMapper) {
        this.claudeClient = claudeClient;
        this.objectMapper = objectMapper;
    }

    public List<ProofreadIssueDto> proofread(String text, String originalText) {
        String response = claudeClient.generateText(systemPrompt(), userPrompt(text, originalText), MAX_OUTPUT_TOKENS);
        return parseIssues(response, text);
    }

    private static String systemPrompt() {
        return """
                You proofread Facebook captions for DASIG, a network of Philippine universities under \
                DOST Region 7. You point out real mistakes; you do not rewrite for style.

                Leave alone:
                - Filipino, Cebuano, and Taglish words and phrasing.
                - Names of people, institutions, programs, and places, and acronyms (DOST, DASIG, CIT-U…).
                - #hashtags, @mentions, emoji, URLs.
                - Text written in decorative Unicode letters (bold/italic/script styles).
                - Deliberate stylistic choices: sentence fragments in a list, capitalised headlines.

                Report only: misspellings, clear grammar errors, and sentences that are genuinely hard to \
                understand. Never invent facts, and never suggest adding content.""";
    }

    private static String userPrompt(String text, String originalText) {
        StringBuilder sb = new StringBuilder();
        boolean comparing = originalText != null && !originalText.isBlank();
        if (comparing) {
            sb.append("A reviewer edited a contributor's caption.\n\nORIGINAL (by the contributor):\n")
              .append(originalText.strip())
              .append("\n\nEDITED (by the reviewer):\n")
              .append(text.strip())
              .append("""


                    Report only problems that appear in EDITED but not in ORIGINAL — mistakes the edit \
                    introduced. Also report, with kind "meaning", any place where the edit changed a name, \
                    date, time, number, place, or claim from ORIGINAL; for those, "suggestion" is "".
                    """);
        } else {
            sb.append("CAPTION:\n").append(text.strip()).append("\n\n");
        }
        sb.append("""
                Respond with ONLY a JSON object, no prose and no code fences:
                {"issues": [{"kind": "spelling|grammar|clarity|meaning", \
                "excerpt": "exact text copied from the caption being checked, as short as possible", \
                "suggestion": "replacement for excerpt", "explanation": "one short sentence"}]}
                At most 10 issues. If there is nothing to report, respond {"issues": []}.""");
        return sb.toString();
    }

    /**
     * Keeps only well-formed findings whose excerpt really occurs in the text
     * (so the UI can locate and replace it), drops no-op suggestions and
     * duplicates, and caps the list.
     */
    List<ProofreadIssueDto> parseIssues(String response, String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(extractJsonObject(response));
        } catch (Exception ex) {
            throw new ClaudeApiException("The writing check returned something unreadable. Please try again.");
        }
        List<ProofreadIssueDto> issues = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : root.path("issues")) {
            String kind = node.path("kind").asText("").strip().toLowerCase(Locale.ROOT);
            String excerpt = node.path("excerpt").asText("");
            String suggestion = node.path("suggestion").asText("");
            String explanation = node.path("explanation").asText("").strip();
            if (!KINDS.contains(kind) || excerpt.isBlank() || !text.contains(excerpt)) {
                continue;
            }
            if (!"meaning".equals(kind) && (suggestion.isBlank() || suggestion.equals(excerpt))) {
                continue;
            }
            if ("meaning".equals(kind)) {
                suggestion = "";
            }
            if (!seen.add(kind + "\u0000" + excerpt)) {
                continue;
            }
            issues.add(new ProofreadIssueDto(kind, excerpt, suggestion, explanation));
            if (issues.size() == MAX_ISSUES) {
                break;
            }
        }
        return issues;
    }

    private static String extractJsonObject(String response) {
        if (response == null) return "";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return start >= 0 && end > start ? response.substring(start, end + 1) : response;
    }
}
