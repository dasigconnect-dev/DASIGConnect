package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dasigconnect.backend.service.SearchQueryParser.ParsedSearchQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SearchQueryParserTest {

    private final SearchQueryParser parser = new SearchQueryParser();

    @Test
    void pureTemporalQuery_isDateOnly_withOneDayRange() {
        ParsedSearchQuery parsed = parser.parse("photos that I uploaded on june 1 2026");

        assertTrue(parsed.dateOnly(), "subjectless date query should be date-only");
        assertTrue(parsed.hasDateRange());
        assertEquals("", parsed.text());
        // June 1 2026 in PH (UTC+8) -> 2026-05-31T16:00:00Z .. 2026-06-01T16:00:00Z
        assertEquals(LocalDate.of(2026, 6, 1).atStartOfDay(SearchQueryParser.ZONE).toInstant(),
                parsed.createdFrom());
        assertEquals(Duration.ofDays(1), Duration.between(parsed.createdFrom(), parsed.createdTo()));
    }

    @Test
    void isoDate_isParsed() {
        ParsedSearchQuery parsed = parser.parse("2026-06-01");
        assertTrue(parsed.dateOnly());
        assertEquals(LocalDate.of(2026, 6, 1).atStartOfDay(SearchQueryParser.ZONE).toInstant(),
                parsed.createdFrom());
        assertEquals(Duration.ofDays(1), Duration.between(parsed.createdFrom(), parsed.createdTo()));
    }

    @Test
    void temporalPlusSubject_keepsSubjectAndDateRange() {
        ParsedSearchQuery parsed = parser.parse("awarding ceremony photos on june 1 2026");

        assertFalse(parsed.dateOnly(), "query has subject text, not date-only");
        assertTrue(parsed.hasDateRange());
        assertTrue(parsed.text().contains("awarding"), () -> "expected subject retained, got: " + parsed.text());
        assertTrue(parsed.text().contains("ceremony"));
        assertFalse(parsed.text().contains("june"), "date phrase must be stripped from embed text");
    }

    @Test
    void nonTemporalQuery_isUnchanged() {
        String q = "students presenting a robotics prototype";
        ParsedSearchQuery parsed = parser.parse(q);

        assertFalse(parsed.dateOnly());
        assertFalse(parsed.hasDateRange());
        assertNull(parsed.createdFrom());
        assertEquals(q, parsed.text(), "non-temporal queries must pass through untouched");
    }

    @Test
    void dayMonthOrder_isParsed() {
        ParsedSearchQuery parsed = parser.parse("1 june 2026");
        assertTrue(parsed.hasDateRange());
        assertEquals(LocalDate.of(2026, 6, 1).atStartOfDay(SearchQueryParser.ZONE).toInstant(),
                parsed.createdFrom());
    }

    @Test
    void yesterday_isOneDayRangeEndingToday() {
        ParsedSearchQuery parsed = parser.parse("yesterday");
        assertTrue(parsed.dateOnly());
        LocalDate today = LocalDate.now(SearchQueryParser.ZONE);
        assertEquals(today.minusDays(1).atStartOfDay(SearchQueryParser.ZONE).toInstant(), parsed.createdFrom());
        assertEquals(today.atStartOfDay(SearchQueryParser.ZONE).toInstant(), parsed.createdTo());
    }

    @Test
    void rangeBoundsAreUtcInstants() {
        ParsedSearchQuery parsed = parser.parse("june 1 2026");
        // sanity: fromUtc is the UTC view of the same instant
        assertEquals(parsed.createdFrom().atOffset(ZoneOffset.UTC), parsed.fromUtc());
    }
}
