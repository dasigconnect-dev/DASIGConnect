package com.dasigconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dasigconnect.backend.model.dto.analytics.PageAudienceInsightsDto;
import com.dasigconnect.backend.model.entity.FacebookPageAudienceSnapshot;
import com.dasigconnect.backend.repository.FacebookPageAudienceSnapshotRepository;
import com.dasigconnect.backend.service.FacebookInsightsClient.PageAudienceSnapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class FacebookPageAudienceServiceTest {

    private static final String PAGE_ID = "PAGE123";

    @Mock private FacebookPageAudienceSnapshotRepository repository;
    @Mock private FacebookInsightsClient insightsClient;
    @Mock private JdbcTemplate jdbcTemplate;

    private FacebookPageAudienceService service;

    @BeforeEach
    void setUp() {
        service = new FacebookPageAudienceService(repository, insightsClient, jdbcTemplate, PAGE_ID);
    }

    private void tableExists(boolean exists) {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Boolean.class))).thenReturn(exists);
    }

    private FacebookPageAudienceSnapshot snapshot(int followers, Instant fetchedAt, String ageGender, String countries) {
        FacebookPageAudienceSnapshot row = new FacebookPageAudienceSnapshot();
        row.setPageId(PAGE_ID);
        row.setFetchedAt(fetchedAt);
        row.setFollowersCount(followers);
        row.setFanCount(followers);
        row.setAgeGender(ageGender);
        row.setCountries(countries);
        return row;
    }

    @Test
    void getAudienceInsights_tableMissing_returnsUnavailable() {
        tableExists(false);
        PageAudienceInsightsDto dto = service.getAudienceInsights("30d");
        assertFalse(dto.available());
    }

    @Test
    void getAudienceInsights_noSnapshots_returnsUnavailable() {
        tableExists(true);
        when(repository.findTopByPageIdOrderByFetchedAtDesc(PAGE_ID)).thenReturn(Optional.empty());
        PageAudienceInsightsDto dto = service.getAudienceInsights("30d");
        assertFalse(dto.available());
    }

    @Test
    void getAudienceInsights_withSnapshots_returnsFollowersTrendAndDemographics() {
        tableExists(true);
        Instant now = Instant.now();
        FacebookPageAudienceSnapshot latest = snapshot(1441, now,
                "{\"F.18-24\":519,\"M.25-34\":362}", "{\"PH\":951,\"VN\":15}");
        when(repository.findTopByPageIdOrderByFetchedAtDesc(PAGE_ID)).thenReturn(Optional.of(latest));
        when(repository.findByPageIdAndFetchedAtAfterOrderByFetchedAtAsc(eq(PAGE_ID), any(Instant.class)))
                .thenReturn(List.of(snapshot(1440, now.minus(2, ChronoUnit.DAYS), null, null), latest));

        PageAudienceInsightsDto dto = service.getAudienceInsights("30d");

        assertTrue(dto.available());
        assertEquals(1441, dto.followersCount());
        assertEquals(1, dto.netFollowerChange());
        assertEquals(2, dto.followerTrend().size());
        assertTrue(dto.demographicsAvailable());
        assertEquals("F · 18-24", dto.ageGender().get(0).label());
        assertEquals(519, dto.ageGender().get(0).value());
        assertEquals(2, dto.countries().size());
        assertEquals("PH", dto.countries().get(0).label());
    }

    @Test
    void syncAudience_storesSnapshot() throws Exception {
        when(insightsClient.isConfigured()).thenReturn(true);
        tableExists(true);
        when(insightsClient.fetchPageAudience()).thenReturn(
                new PageAudienceSnapshot(1441, 1441, "{\"F.18-24\":519}", "{\"PH\":951}", null, "{}"));

        assertTrue(service.syncAudience());
        verify(repository).save(any(FacebookPageAudienceSnapshot.class));
    }

    @Test
    void syncAudience_notConfigured_returnsFalse() {
        when(insightsClient.isConfigured()).thenReturn(false);
        assertFalse(service.syncAudience());
        verify(repository, never()).save(any());
    }
}
