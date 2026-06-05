package com.dasigconnect.backend.model.dto.media;

import java.util.List;

/** Response for the media search autocomplete endpoint (UC-4.5). */
public record MediaSearchSuggestionsResponseDto(String query, List<MediaSearchSuggestionDto> suggestions) {}
