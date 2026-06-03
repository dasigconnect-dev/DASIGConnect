package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Stage-0 deterministic intent router for media search (UC-4.5).
 *
 * <p>Detects temporal expressions ("uploaded on June 1", "yesterday", "last week",
 * "2026-06-01") in a natural-language query, converts them to a half-open
 * {@code [from, to)} UTC instant range over {@code created_at}, and strips them
 * (plus filler words) from the text that gets embedded/lexically searched. This
 * keeps date tokens out of the semantic vector and lets the service apply a real
 * date filter instead of returning relevance-ranked results regardless of date.
 *
 * <p>Dates are interpreted in Philippine local time (UTC+8), matching how
 * contributors think about "the day I uploaded it", then converted to UTC because
 * {@code media_assets.created_at} is stored as {@code timestamptz}.
 */
@Component
public class SearchQueryParser {

    /** Contributors are in the Philippines; "June 1" means a PH calendar day. */
    static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("january", 1), Map.entry("jan", 1),
            Map.entry("february", 2), Map.entry("feb", 2),
            Map.entry("march", 3), Map.entry("mar", 3),
            Map.entry("april", 4), Map.entry("apr", 4),
            Map.entry("may", 5),
            Map.entry("june", 6), Map.entry("jun", 6),
            Map.entry("july", 7), Map.entry("jul", 7),
            Map.entry("august", 8), Map.entry("aug", 8),
            Map.entry("september", 9), Map.entry("sept", 9), Map.entry("sep", 9),
            Map.entry("october", 10), Map.entry("oct", 10),
            Map.entry("november", 11), Map.entry("nov", 11),
            Map.entry("december", 12), Map.entry("dec", 12));

    // Full names first so "june" wins over "jun" in the alternation.
    private static final String MONTH_ALT =
            "january|february|march|april|june|july|august|september|october|november|december"
            + "|jan|feb|mar|apr|may|jun|jul|aug|sept|sep|oct|nov|dec";

    private static final Pattern ISO_DATE =
            Pattern.compile("(?<![0-9])(\\d{4})-(\\d{1,2})-(\\d{1,2})(?![0-9])");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b(" + MONTH_ALT + ")\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY_MONTH = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(" + MONTH_ALT + ")\\.?(?:\\s*,?\\s*(\\d{4}))?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_N = Pattern.compile(
            "\\b(?:last|past|previous)\\s+(\\d{1,3})\\s+(day|days|week|weeks)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Words that carry no search meaning once the date is extracted. */
    private static final Set<String> STOPWORDS = Set.of(
            "find", "show", "search", "get", "give", "list", "me", "my", "our", "the", "a", "an",
            "all", "any", "some", "photo", "photos", "picture", "pictures", "image", "images",
            "media", "file", "files", "asset", "assets", "that", "which", "was", "were", "is", "are",
            "i", "we", "uploaded", "upload", "uploads", "posted", "post", "taken", "take", "added",
            "on", "at", "from", "in", "of", "during", "dated", "date", "for", "please", "with",
            "to", "and");

    public ParsedSearchQuery parse(String rawQuery) {
        String original = rawQuery == null ? "" : rawQuery.trim();
        if (original.isEmpty()) {
            return new ParsedSearchQuery("", null, null, false);
        }

        DateMatch match = firstMatch(original);
        if (match == null) {
            // No temporal intent — preserve the existing semantic/lexical behavior.
            return new ParsedSearchQuery(original, null, null, false);
        }

        Instant from = match.startDay.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = match.endDayExclusive.atStartOfDay(ZONE).toInstant();

        String withoutDate = (original.substring(0, match.start) + " " + original.substring(match.end))
                .replaceAll("\\s+", " ")
                .trim();
        String residual = stripStopwords(withoutDate);
        boolean dateOnly = residual.isBlank();

        return new ParsedSearchQuery(dateOnly ? "" : residual, from, toExclusive, dateOnly);
    }

    private DateMatch firstMatch(String text) {
        DateMatch m = matchIso(text);
        if (m == null) m = matchMonthDay(text);
        if (m == null) m = matchDayMonth(text);
        if (m == null) m = matchRelative(text);
        return m;
    }

    private DateMatch matchIso(String text) {
        Matcher matcher = ISO_DATE.matcher(text);
        if (!matcher.find()) return null;
        LocalDate day = safeDate(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        if (day == null) return null;
        return new DateMatch(matcher.start(), matcher.end(), day, day.plusDays(1));
    }

    private DateMatch matchMonthDay(String text) {
        Matcher matcher = MONTH_DAY.matcher(text);
        if (!matcher.find()) return null;
        Integer month = MONTHS.get(matcher.group(1).toLowerCase());
        if (month == null) return null;
        int year = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : defaultYear(month, parseDay(matcher.group(2)));
        LocalDate day = safeDate(year, month, parseDay(matcher.group(2)));
        if (day == null) return null;
        return new DateMatch(matcher.start(), matcher.end(), day, day.plusDays(1));
    }

    private DateMatch matchDayMonth(String text) {
        Matcher matcher = DAY_MONTH.matcher(text);
        if (!matcher.find()) return null;
        Integer month = MONTHS.get(matcher.group(2).toLowerCase());
        if (month == null) return null;
        int year = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : defaultYear(month, parseDay(matcher.group(1)));
        LocalDate day = safeDate(year, month, parseDay(matcher.group(1)));
        if (day == null) return null;
        return new DateMatch(matcher.start(), matcher.end(), day, day.plusDays(1));
    }

    private DateMatch matchRelative(String text) {
        LocalDate today = LocalDate.now(ZONE);

        DateMatch keyword = matchKeyword(text, "\\btoday\\b", today, today.plusDays(1));
        if (keyword != null) return keyword;
        keyword = matchKeyword(text, "\\byesterday\\b", today.minusDays(1), today);
        if (keyword != null) return keyword;

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        keyword = matchKeyword(text, "\\bthis week\\b", weekStart, weekStart.plusWeeks(1));
        if (keyword != null) return keyword;
        keyword = matchKeyword(text, "\\blast week\\b", weekStart.minusWeeks(1), weekStart);
        if (keyword != null) return keyword;

        LocalDate monthStart = today.withDayOfMonth(1);
        keyword = matchKeyword(text, "\\bthis month\\b", monthStart, monthStart.plusMonths(1));
        if (keyword != null) return keyword;
        keyword = matchKeyword(text, "\\blast month\\b", monthStart.minusMonths(1), monthStart);
        if (keyword != null) return keyword;

        Matcher lastN = LAST_N.matcher(text);
        if (lastN.find()) {
            int n = Integer.parseInt(lastN.group(1));
            boolean weeks = lastN.group(2).toLowerCase().startsWith("week");
            LocalDate start = weeks ? today.minusWeeks(n) : today.minusDays(n);
            return new DateMatch(lastN.start(), lastN.end(), start, today.plusDays(1));
        }
        return null;
    }

    private DateMatch matchKeyword(String text, String regex, LocalDate start, LocalDate endExclusive) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        if (!matcher.find()) return null;
        return new DateMatch(matcher.start(), matcher.end(), start, endExclusive);
    }

    private int parseDay(String value) {
        return Integer.parseInt(value);
    }

    /** Year omitted: assume the most recent occurrence (this year, or last year if still future). */
    private int defaultYear(int month, int day) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate candidate = safeDate(today.getYear(), month, day);
        if (candidate != null && candidate.isAfter(today)) {
            return today.getYear() - 1;
        }
        return today.getYear();
    }

    private LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException ex) {
            return null;
        }
    }

    private String stripStopwords(String text) {
        if (text.isBlank()) return "";
        return Arrays.stream(text.toLowerCase().replaceAll("[^a-z0-9\\s]+", " ").split("\\s+"))
                .filter(token -> !token.isBlank() && !STOPWORDS.contains(token))
                .reduce((a, b) -> a + " " + b)
                .orElse("")
                .trim();
    }

    private record DateMatch(int start, int end, LocalDate startDay, LocalDate endDayExclusive) {}

    /**
     * Result of Stage-0 parsing.
     *
     * @param text         text to embed / lexically search (date phrase and filler removed)
     * @param createdFrom  inclusive lower bound on {@code created_at}, or null
     * @param createdTo    exclusive upper bound on {@code created_at}, or null
     * @param dateOnly     true when the query is purely temporal (no remaining subject text)
     */
    public record ParsedSearchQuery(String text, Instant createdFrom, Instant createdTo, boolean dateOnly) {
        public boolean hasDateRange() {
            return createdFrom != null && createdTo != null;
        }

        java.time.OffsetDateTime fromUtc() {
            return createdFrom == null ? null : createdFrom.atOffset(ZoneOffset.UTC);
        }

        java.time.OffsetDateTime toUtc() {
            return createdTo == null ? null : createdTo.atOffset(ZoneOffset.UTC);
        }
    }
}
