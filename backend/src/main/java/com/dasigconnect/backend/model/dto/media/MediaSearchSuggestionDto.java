package com.dasigconnect.backend.model.dto.media;

/**
 * One related-search suggestion for the media search bar.
 *
 * @param text the query text applied when the suggestion is selected
 * @param type one of: tag, file, uploader, collection, folder
 */
public record MediaSearchSuggestionDto(String text, String type) {}
