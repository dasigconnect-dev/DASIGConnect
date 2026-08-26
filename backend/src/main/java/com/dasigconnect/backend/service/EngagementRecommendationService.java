package com.dasigconnect.backend.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dasigconnect.backend.model.dto.engagement.EngagementRecommendationDto;
import com.dasigconnect.backend.model.dto.engagement.EngagementRecommendationDto.RecommendedSlotDto;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.FacebookEngagementAnalyticsClient.EngagementSample;

@Service
public class EngagementRecommendationService {

    static final ZoneId PAGE_ZONE = ZoneId.of("Asia/Manila");
    static final int MINIMUM_SAMPLE_SIZE = 20;
    private static final int MAX_SLOTS = 3;
    private static final List<PeakWindow> DEFAULT_WINDOWS = List.of(
            new PeakWindow(DayOfWeek.TUESDAY, 18, 1),
            new PeakWindow(DayOfWeek.WEDNESDAY, 18, .9),
            new PeakWindow(DayOfWeek.THURSDAY, 18, .8));

    private final FacebookEngagementAnalyticsClient analyticsClient;
    private final GuardRailService guardRailService;

    public EngagementRecommendationService(
            FacebookEngagementAnalyticsClient analyticsClient,
            GuardRailService guardRailService) {
        this.analyticsClient = analyticsClient;
        this.guardRailService = guardRailService;
    }

    public EngagementRecommendationDto recommend(JwtUserDetails user, UUID requestedInstitutionId) {
        UUID institutionId = user.institutionId() != null ? user.institutionId() : requestedInstitutionId;
        try {
            List<EngagementSample> samples = analyticsClient.fetchRecentPostEngagement();
            List<PeakWindow> rankedWindows = rankWindows(samples);
            boolean historical = samples.size() >= MINIMUM_SAMPLE_SIZE && !rankedWindows.isEmpty();
            List<PeakWindow> windows = historical ? rankedWindows : DEFAULT_WINDOWS;
            List<RecommendedSlotDto> slots = buildSlots(windows, institutionId);
            return new EngagementRecommendationDto(
                    true,
                    historical ? "HISTORICAL" : "DEFAULT",
                    historical
                            ? "Based on recent DASIG Facebook Page engagement."
                            : "Using general weekday evening guidance. Recommendations will improve as more Facebook history is collected.",
                    PAGE_ZONE.getId(), samples.size(), slots);
        } catch (Exception exception) {
            return new EngagementRecommendationDto(false, "UNAVAILABLE", null,
                    PAGE_ZONE.getId(), 0, List.of());
        }
    }

    private List<PeakWindow> rankWindows(List<EngagementSample> samples) {
        Map<WindowKey, WindowStats> stats = new LinkedHashMap<>();
        for (EngagementSample sample : samples) {
            var local = sample.publishedAt().atZone(PAGE_ZONE);
            if (local.getHour() < 8 || local.getHour() >= 20) {
                continue;
            }
            WindowKey key = new WindowKey(local.getDayOfWeek(), local.getHour());
            stats.computeIfAbsent(key, ignored -> new WindowStats()).add(sample.engagementScore());
        }
        return stats.entrySet().stream()
                .map(entry -> new PeakWindow(entry.getKey().day(), entry.getKey().hour(), entry.getValue().average()))
                .sorted(Comparator.comparingDouble(PeakWindow::score).reversed())
                .limit(5).toList();
    }

    private List<RecommendedSlotDto> buildSlots(List<PeakWindow> windows, UUID institutionId) {
        List<RecommendedSlotDto> slots = new ArrayList<>();
        LocalDate today = LocalDate.now(PAGE_ZONE);
        Instant now = Instant.now();
        for (int offset = 0; offset <= 30 && slots.size() < MAX_SLOTS; offset++) {
            LocalDate date = today.plusDays(offset);
            for (PeakWindow window : windows) {
                if (date.getDayOfWeek() != window.day()) {
                    continue;
                }
                Instant candidate = LocalDateTime.of(date, LocalTime.of(window.hour(), 0))
                        .atZone(PAGE_ZONE).toInstant();
                if (!candidate.isAfter(Instant.now())) {
                    continue;
                }
                GuardRailResult guardRails = guardRailService.validate(institutionId, candidate);
                if (guardRails.isBlocked()) {
                    continue;
                }
                List<String> warnings = guardRails.getSoftWarnings().stream()
                        .map(item -> item.getMessage()).toList();
                String day = window.day().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                slots.add(new RecommendedSlotDto(candidate,
                        "Best engagement: " + day + "s " + displayHour(window.hour())
                        + " - " + displayHour(window.hour() + 1),
                        Math.round(window.score() * 10.0) / 10.0, warnings));
                if (slots.size() == MAX_SLOTS) {
                    break;
                }
            }
        }
        return slots;
    }

    private static String displayHour(int hour) {
        int normalized = hour % 24;
        int twelveHour = normalized % 12 == 0 ? 12 : normalized % 12;
        return twelveHour + (normalized < 12 ? " AM" : " PM");
    }

    private record WindowKey(DayOfWeek day, int hour) {

    }

    private record PeakWindow(DayOfWeek day, int hour, double score) {

    }

    private static final class WindowStats {

        private double total;
        private int count;

        void add(double score) {
            total += score;
            count++;
        }

        double average() {
            return count == 0 ? 0 : total / count;
        }
    }
}
