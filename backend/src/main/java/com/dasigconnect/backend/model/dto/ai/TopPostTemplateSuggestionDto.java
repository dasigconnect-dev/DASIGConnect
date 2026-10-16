package com.dasigconnect.backend.model.dto.ai;

import java.time.Instant;
import java.util.List;

/**
 * A caption template drafted by AI from the best-performing Page posts.
 * Nothing is saved: the caller reviews it and saves it through the normal
 * post-template endpoint.
 *
 * <p>When {@code available} is false there wasn't enough real engagement to
 * learn from; {@code reason} says why and every other field is empty.
 */
public record TopPostTemplateSuggestionDto(
        boolean available,
        String reason,
        String name,
        String caption,
        List<String> tags,
        List<String> insights,
        String source,
        int postsConsidered,
        List<SourcePost> topPosts) {

    /** One of the high-performing posts the template was based on. */
    public record SourcePost(String excerpt, Instant publishedAt, long reactions, long comments, long shares) {}

    public static TopPostTemplateSuggestionDto unavailable(String reason, String source, int postsConsidered) {
        return new TopPostTemplateSuggestionDto(false, reason, null, null, List.of(), List.of(),
                source, postsConsidered, List.of());
    }
}
