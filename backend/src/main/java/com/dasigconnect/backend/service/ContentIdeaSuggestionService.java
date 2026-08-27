package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.repository.SubmissionRepository;

/**
 * Generates 2-3 AI-assisted topic suggestions for an institution when T-07
 * (Empty Schedule Warning) fires (A6).
 *
 * Synthesizes:
 * 1. Seasonal / historical signal: prior posting history from the institution.
 * 2. Cross-institution signal: recent categories posted by partner institutions.
 *
 * If insufficient data exists, returns an empty list without fabricating
 * suggestions (A6a).
 */
@Service
public class ContentIdeaSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(ContentIdeaSuggestionService.class);

    private final SubmissionRepository submissionRepository;
    private final ClaudeVisionClient claudeClient;

    public ContentIdeaSuggestionService(
            SubmissionRepository submissionRepository,
            ClaudeVisionClient claudeClient) {
        this.submissionRepository = submissionRepository;
        this.claudeClient = claudeClient;
    }

    public List<String> generateSuggestions(Institution institution) {
        if (institution == null) return List.of();
        UUID instId = institution.getId();

        try {
            // Signal 1: Historical posts from this institution
            List<String> historicalTitles = submissionRepository.findRecentAndHistoricalPostTitles(instId);

            // Signal 2: Recent categories from partner institutions
            Instant sixtyDaysAgo = Instant.now().minus(60, ChronoUnit.DAYS);
            List<String> otherCategories = submissionRepository.findRecentCategoriesFromOtherInstitutions(instId, sixtyDaysAgo);

            // A6a: Insufficient data guard
            if (historicalTitles.isEmpty() && otherCategories.isEmpty()) {
                log.info("Insufficient historical or partner signals for institution {} — skipping AI suggestions.", institution.getName());
                return List.of();
            }

            String prompt = buildPrompt(institution.getName(), historicalTitles, otherCategories);
            String system = "You are an AI assistant for DASIGConnect (DOST Region 7 social media platform). "
                    + "Your goal is to suggest 2-3 relevant post ideas for a university/institution whose schedule is empty.";

            String response = claudeClient.generateText(system, prompt);
            return parseBulletPoints(response);
        } catch (Exception ex) {
            log.warn("AI content idea suggestion failed for {}: {}", institution.getName(), ex.getMessage());
            return List.of();
        }
    }

    private String buildPrompt(String instName, List<String> historical, List<String> otherCategories) {
        StringBuilder sb = new StringBuilder();
        sb.append("Institution '").append(instName).append("' has no social media posts scheduled for next week.\n\n");

        if (!historical.isEmpty()) {
            sb.append("Historical posts from this institution:\n");
            for (String h : historical) {
                sb.append("- ").append(h).append("\n");
            }
            sb.append("\n");
        }

        if (!otherCategories.isEmpty()) {
            sb.append("Popular categories recently posted by other partner institutions in DASIG:\n");
            for (String cat : otherCategories) {
                sb.append("- ").append(cat).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Please provide 2 to 3 concise, actionable, plain-language topic suggestions for '")
          .append(instName)
          .append("'.\n")
          .append("Return each suggestion as a single plain bullet point starting with '• '. Do not include introductory text or conclusions.");

        return sb.toString();
    }

    private List<String> parseBulletPoints(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> results = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            } else if (trimmed.matches("^\\d+[.)]\\s+.*")) {
                trimmed = trimmed.replaceFirst("^\\d+[.)]\\s+", "").trim();
            }
            if (!trimmed.isBlank() && !trimmed.toLowerCase().contains("suggestion") && trimmed.length() > 5) {
                results.add(trimmed);
            }
            if (results.size() >= 3) break;
        }
        return results;
    }
}
