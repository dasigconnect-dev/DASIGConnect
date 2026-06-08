package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.analytics.PageAudienceInsightsDto;
import com.dasigconnect.backend.model.dto.analytics.PageAudienceInsightsDto.DemographicSlice;
import com.dasigconnect.backend.model.dto.analytics.PageAudienceInsightsDto.FollowerPoint;
import com.dasigconnect.backend.model.entity.FacebookPageAudienceSnapshot;
import com.dasigconnect.backend.repository.FacebookPageAudienceSnapshotRepository;
import com.dasigconnect.backend.service.FacebookInsightsClient.PageAudienceSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * UC-4.8 Phase 5b: syncs and serves PAGE-level Facebook audience (follower growth + best-effort
 * demographics) for the single DASIG page. Page-global data — not institution-scoped. The Graph
 * call happens outside any DB transaction (HikariCP-5 discipline); persistence is a short save.
 */
@Service
public class FacebookPageAudienceService {

    private static final Logger log = LoggerFactory.getLogger(FacebookPageAudienceService.class);
    private static final int MAX_DEMOGRAPHIC_ROWS = 8;

    private final FacebookPageAudienceSnapshotRepository repository;
    private final FacebookInsightsClient insightsClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String pageId;

    public FacebookPageAudienceService(
            FacebookPageAudienceSnapshotRepository repository,
            FacebookInsightsClient insightsClient,
            JdbcTemplate jdbcTemplate,
            @Value("${app.facebook.page-id:}") String pageId) {
        this.repository = repository;
        this.insightsClient = insightsClient;
        this.jdbcTemplate = jdbcTemplate;
        this.pageId = pageId;
    }

    /** Fetch a fresh page-audience snapshot. Returns true when a snapshot was stored. */
    public boolean syncAudience() {
        if (pageId == null || pageId.isBlank() || !insightsClient.isConfigured()) {
            log.info("Page-audience sync skipped because Facebook page settings are incomplete.");
            return false;
        }
        if (!tableExists()) {
            log.info("Page-audience sync skipped because facebook_page_audience_snapshots is not migrated yet.");
            return false;
        }
        try {
            PageAudienceSnapshot snapshot = insightsClient.fetchPageAudience();
            FacebookPageAudienceSnapshot row = new FacebookPageAudienceSnapshot();
            row.setPageId(pageId);
            row.setFetchedAt(Instant.now());
            row.setFollowersCount(snapshot.followersCount());
            row.setFanCount(snapshot.fanCount());
            row.setAgeGender(snapshot.ageGenderJson());
            row.setCountries(snapshot.countriesJson());
            row.setCities(snapshot.citiesJson());
            row.setRaw(snapshot.rawJson());
            repository.save(row);
            return true;
        } catch (Exception ex) {
            log.warn("Page-audience sync failed: {}", ex.getMessage());
            return false;
        }
    }

    /** Read model for the Audience tab: latest followers/demographics + follower trend in range. */
    public PageAudienceInsightsDto getAudienceInsights(String range) {
        if (!tableExists()) {
            return PageAudienceInsightsDto.unavailable();
        }
        Optional<FacebookPageAudienceSnapshot> latestOpt = repository.findTopByPageIdOrderByFetchedAtDesc(pageId);
        if (latestOpt.isEmpty()) {
            return PageAudienceInsightsDto.unavailable();
        }
        FacebookPageAudienceSnapshot latest = latestOpt.get();

        List<FacebookPageAudienceSnapshot> series =
                repository.findByPageIdAndFetchedAtAfterOrderByFetchedAtAsc(pageId, rangeStart(range));
        List<FollowerPoint> trend = series.stream()
                .map(s -> new FollowerPoint(s.getFetchedAt().toString(), s.getFollowersCount()))
                .toList();

        int netChange = 0;
        Integer firstFollowers = series.stream().map(FacebookPageAudienceSnapshot::getFollowersCount)
                .filter(value -> value != null).findFirst().orElse(null);
        if (firstFollowers != null && latest.getFollowersCount() != null) {
            netChange = latest.getFollowersCount() - firstFollowers;
        }

        List<DemographicSlice> ageGender = parseTopDemographics(latest.getAgeGender(), Integer.MAX_VALUE);
        List<DemographicSlice> countries = parseTopDemographics(latest.getCountries(), MAX_DEMOGRAPHIC_ROWS);
        List<DemographicSlice> cities = parseTopDemographics(latest.getCities(), MAX_DEMOGRAPHIC_ROWS);
        boolean demographicsAvailable = !ageGender.isEmpty() || !countries.isEmpty() || !cities.isEmpty();

        return new PageAudienceInsightsDto(
                true,
                latest.getFetchedAt().toString(),
                latest.getFollowersCount(),
                latest.getFanCount(),
                netChange,
                trend,
                demographicsAvailable,
                ageGender,
                countries,
                cities);
    }

    /** Parse a `{ "key": count }` Graph value object into a sorted, top-N slice list. */
    private List<DemographicSlice> parseTopDemographics(String json, int limit) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return List.of();
            }
            List<DemographicSlice> slices = new ArrayList<>();
            node.properties().forEach(entry -> {
                if (entry.getValue().isNumber()) {
                    slices.add(new DemographicSlice(prettyLabel(entry.getKey()), entry.getValue().asInt()));
                }
            });
            slices.sort(Comparator.comparingInt(DemographicSlice::value).reversed());
            return limit >= slices.size() ? slices : slices.subList(0, limit);
        } catch (Exception ex) {
            log.warn("Could not parse page-audience demographic JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    /** "M.25-34" -> "M · 25-34"; country/city keys pass through. */
    private static String prettyLabel(String key) {
        return key.replace(".", " · ");
    }

    private Instant rangeStart(String range) {
        Instant now = Instant.now();
        return switch (range == null ? "30d" : range) {
            case "7d" -> now.minus(7, ChronoUnit.DAYS);
            case "90d" -> now.minus(90, ChronoUnit.DAYS);
            case "ytd" -> ZonedDateTime.now(ZoneOffset.UTC).withDayOfYear(1)
                    .truncatedTo(ChronoUnit.DAYS).toInstant();
            default -> now.minus(30, ChronoUnit.DAYS);
        };
    }

    private boolean tableExists() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.facebook_page_audience_snapshots') IS NOT NULL",
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }
}
