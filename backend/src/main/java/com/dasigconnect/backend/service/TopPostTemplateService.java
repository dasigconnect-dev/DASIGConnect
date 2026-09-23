package com.dasigconnect.backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.dasigconnect.backend.external.ClaudeVisionClient;
import com.dasigconnect.backend.external.ClaudeVisionClient.ClaudeApiException;
import com.dasigconnect.backend.model.dto.ai.TopPostTemplateSuggestionDto;
import com.dasigconnect.backend.model.dto.ai.TopPostTemplateSuggestionDto.SourcePost;
import com.dasigconnect.backend.repository.SubmissionEngagementMetricRepository;
import com.dasigconnect.backend.service.FacebookEngagementAnalyticsClient.PagePostSample;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Drafts a reusable caption template from the Page's best-performing posts.
 *
 * <p>Source: the connected Facebook Page's last 100 posts (which already
 * include everything DASIGConnect published there, plus older posts). If the
 * Page can't be read, falls back to DASIGConnect's own synced engagement.
 *
 * <p>Posts are ranked by reactions + 2×comments + 3×shares — a comment or share
 * signals more intent than a reaction. Reach isn't used: it is unavailable for
 * most posts (missing read_insights).
 *
 * <p>Refuses to generate when fewer than {@link #MIN_ENGAGED_POSTS} posts have
 * any engagement — a "pattern" from a handful of 1–2 reaction posts would be
 * noise dressed up as insight.
 *
 * <p>Nothing is persisted here; the caller saves the draft through
 * {@code POST /post-templates} after reviewing it. The Claude call runs outside
 * any transaction so no pooled connection is held during it.
 */
@Service
public class TopPostTemplateService {

    private static final Logger log = LoggerFactory.getLogger(TopPostTemplateService.class);

    static final int MIN_ENGAGED_POSTS = 8;
    static final int TOP_POSTS = 6;
    static final int CONTRAST_POSTS = 3;
    static final String SOURCE_FACEBOOK = "facebook_page";
    static final String SOURCE_DASIGCONNECT = "dasigconnect";

    private static final int LOCAL_FALLBACK_LIMIT = 100;
    private static final int PROMPT_EXCERPT_CHARS = 700;
    private static final int DISPLAY_EXCERPT_CHARS = 160;
    private static final int MAX_CAPTION_CODE_POINTS = 3000;
    private static final int MAX_NAME_CHARS = 80;
    private static final int MAX_TAGS = 4;
    private static final int MAX_INSIGHTS = 4;
    private static final int MAX_OUTPUT_TOKENS = 1500;

    private final FacebookEngagementAnalyticsClient facebookClient;
    private final SubmissionEngagementMetricRepository engagementRepository;
    private final ClaudeVisionClient claudeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TopPostTemplateService(
            FacebookEngagementAnalyticsClient facebookClient,
            SubmissionEngagementMetricRepository engagementRepository,
            ClaudeVisionClient claudeClient) {
        this.facebookClient = facebookClient;
        this.engagementRepository = engagementRepository;
        this.claudeClient = claudeClient;
    }

    public TopPostTemplateSuggestionDto suggest() {
        LoadedPosts loaded = loadPosts();
        List<Post> posts = loaded.posts().stream()
                .filter(post -> post.text() != null && !post.text().isBlank())
                .toList();
        List<Post> engaged = posts.stream().filter(post -> post.score() > 0).toList();

        if (engaged.size() < MIN_ENGAGED_POSTS) {
            String reason = "Only " + engaged.size() + " of the last " + posts.size()
                    + " posts have any reactions, comments, or shares. At least " + MIN_ENGAGED_POSTS
                    + " are needed to find a real pattern — try again once more posts have been published.";
            return TopPostTemplateSuggestionDto.unavailable(reason, loaded.source(), posts.size());
        }

        List<Post> ranked = posts.stream()
                .sorted(Comparator.comparingLong(Post::score).reversed()
                        .thenComparing(Post::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Post> top = ranked.subList(0, TOP_POSTS);
        // Contrast only when there are clearly separate low performers to compare against.
        List<Post> contrast = ranked.size() >= TOP_POSTS + CONTRAST_POSTS + 2
                ? ranked.subList(ranked.size() - CONTRAST_POSTS, ranked.size())
                : List.of();

        String response = claudeClient.generateText(systemPrompt(), buildPrompt(top, contrast), MAX_OUTPUT_TOKENS);
        Draft draft = parseDraft(response);

        List<SourcePost> sources = top.stream()
                .map(post -> new SourcePost(excerpt(post.text(), DISPLAY_EXCERPT_CHARS), post.publishedAt(),
                        post.reactions(), post.comments(), post.shares()))
                .toList();
        return new TopPostTemplateSuggestionDto(true, null, draft.name(), draft.caption(), draft.tags(),
                draft.insights(), loaded.source(), posts.size(), sources);
    }

    /** reactions + 2×comments + 3×shares. */
    static long score(long reactions, long comments, long shares) {
        return reactions + 2 * comments + 3 * shares;
    }

    private LoadedPosts loadPosts() {
        try {
            List<PagePostSample> pagePosts = facebookClient.fetchRecentPostsWithText();
            if (!pagePosts.isEmpty()) {
                return new LoadedPosts(SOURCE_FACEBOOK, pagePosts.stream()
                        .map(p -> new Post(p.message(), p.publishedAt(), p.reactions(), p.comments(), p.shares()))
                        .toList());
            }
        } catch (IOException ex) {
            log.info("Facebook Page history unavailable for template generation, using DASIGConnect data: {}",
                    ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        List<Post> local = engagementRepository
                .findPublishedCaptionsWithEngagement(PageRequest.of(0, LOCAL_FALLBACK_LIMIT))
                .stream()
                .map(row -> new Post((String) row[0], (Instant) row[1],
                        toLong(row[2]), toLong(row[3]), toLong(row[4])))
                .toList();
        return new LoadedPosts(SOURCE_DASIGCONNECT, local);
    }

    private static String systemPrompt() {
        return "You write reusable Facebook caption templates for the DOST Academe-Science and Innovation Group "
                + "(DASIG), a network of higher-education institutions under DOST Region 7 in the Philippines. "
                + "You base every recommendation strictly on the posts you are given and never invent facts, "
                + "names, dates, or statistics.";
    }

    private static String buildPrompt(List<Post> top, List<Post> contrast) {
        StringBuilder sb = new StringBuilder();
        sb.append("Below are the Page's best-performing posts, ranked by engagement ")
          .append("(score = reactions + 2*comments + 3*shares).\n\n");
        appendPosts(sb, "TOP POST", top);
        if (!contrast.isEmpty()) {
            sb.append("For contrast, these posts performed worst:\n\n");
            appendPosts(sb, "LOW POST", contrast);
        }
        sb.append("""
                Task: find what the top posts have in common that the others lack — opening line, structure, \
                length, tone, emoji use, hashtags, call to action — and turn it into ONE reusable caption template.

                Rules for the template caption:
                - Replace every event-specific detail with a square-bracket placeholder, e.g. [EVENT TITLE], \
                [DATE], [VENUE], [KEY HIGHLIGHT], [CALL TO ACTION].
                - Keep hashtags literally only if they recur across several top posts.
                - Do not copy any single post verbatim.
                - Keep it under 1500 characters.

                Respond with ONLY a JSON object, no prose and no code fences:
                {
                  "name": "short template name, max 60 characters",
                  "caption": "the template caption, using \\n for line breaks",
                  "tags": ["1 to 4 short topic labels without #"],
                  "insights": ["2 to 4 short sentences, each naming a concrete pattern you saw in the top posts"]
                }
                """);
        return sb.toString();
    }

    private static void appendPosts(StringBuilder sb, String label, List<Post> posts) {
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            sb.append(label).append(' ').append(i + 1)
              .append(" (reactions ").append(post.reactions())
              .append(", comments ").append(post.comments())
              .append(", shares ").append(post.shares())
              .append("):\n")
              .append(excerpt(post.text(), PROMPT_EXCERPT_CHARS))
              .append("\n\n");
        }
    }

    Draft parseDraft(String response) {
        JsonNode root;
        try {
            root = objectMapper.readTree(extractJsonObject(response));
        } catch (Exception ex) {
            throw new ClaudeApiException("AI returned a template that couldn't be read. Please try again.");
        }
        String caption = root.path("caption").asText("").strip();
        if (caption.isEmpty()) {
            throw new ClaudeApiException("AI returned an empty template. Please try again.");
        }
        if (caption.codePointCount(0, caption.length()) > MAX_CAPTION_CODE_POINTS) {
            throw new ClaudeApiException("AI returned a template longer than the caption limit. Please try again.");
        }
        String name = root.path("name").asText("").strip();
        if (name.isEmpty()) name = "Top Posts Template";
        if (name.length() > MAX_NAME_CHARS) name = name.substring(0, MAX_NAME_CHARS).strip();

        Set<String> tags = new LinkedHashSet<>();
        for (JsonNode tag : root.path("tags")) {
            String clean = tag.asText("").replace("#", "").replace(",", "").strip();
            if (!clean.isEmpty()) tags.add(clean);
            if (tags.size() == MAX_TAGS) break;
        }
        List<String> insights = new ArrayList<>();
        for (JsonNode insight : root.path("insights")) {
            String clean = insight.asText("").strip();
            if (!clean.isEmpty()) insights.add(clean);
            if (insights.size() == MAX_INSIGHTS) break;
        }
        return new Draft(name, caption, List.copyOf(tags), List.copyOf(insights));
    }

    /** Tolerates code fences or stray prose around the JSON object. */
    private static String extractJsonObject(String response) {
        if (response == null) return "";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return start >= 0 && end > start ? response.substring(start, end + 1) : response;
    }

    private static String excerpt(String text, int maxChars) {
        String flat = text.strip();
        return flat.length() <= maxChars ? flat : flat.substring(0, maxChars).strip() + "…";
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    record Post(String text, Instant publishedAt, long reactions, long comments, long shares) {
        long score() {
            return TopPostTemplateService.score(reactions, comments, shares);
        }
    }

    record Draft(String name, String caption, List<String> tags, List<String> insights) {}

    private record LoadedPosts(String source, List<Post> posts) {}
}
