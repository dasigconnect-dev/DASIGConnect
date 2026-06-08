package com.dasigconnect.backend.model.dto.analytics;

import java.util.List;

/**
 * UC-4.8 Phase 5b: page-level audience for the Audience insights tab. {@code available} is
 * false until the first sync stores a snapshot; {@code demographicsAvailable} is false when
 * Meta's deprecated page_fans_* breakdowns returned nothing.
 */
public record PageAudienceInsightsDto(
        boolean available,
        String latestFetchedAt,
        Integer followersCount,
        Integer fanCount,
        int netFollowerChange,
        List<FollowerPoint> followerTrend,
        boolean demographicsAvailable,
        List<DemographicSlice> ageGender,
        List<DemographicSlice> countries,
        List<DemographicSlice> cities) {

    public record FollowerPoint(String date, Integer followers) {}

    public record DemographicSlice(String label, int value) {}

    public static PageAudienceInsightsDto unavailable() {
        return new PageAudienceInsightsDto(false, null, null, null, 0, List.of(), false, List.of(), List.of(), List.of());
    }
}
