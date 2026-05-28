package com.dasigconnect.backend.model.dto.analytics;

public record AdminAnalyticsDto(
        long facebookApiFailureCount,
        long administratorActions,
        long adminDirectPosts) {
}
