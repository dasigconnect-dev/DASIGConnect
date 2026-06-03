package com.dasigconnect.backend.model.dto.media;

import java.util.List;

public class AlbumPromptSuggestionResponseDto {

    private String prompt;
    private String suggestedName;
    private int totalCandidates;
    private List<AlbumPromptSuggestionAssetDto> candidates;

    public AlbumPromptSuggestionResponseDto(String prompt, String suggestedName, int totalCandidates,
                                            List<AlbumPromptSuggestionAssetDto> candidates) {
        this.prompt = prompt;
        this.suggestedName = suggestedName;
        this.totalCandidates = totalCandidates;
        this.candidates = candidates;
    }

    public String getPrompt() { return prompt; }
    public String getSuggestedName() { return suggestedName; }
    public int getTotalCandidates() { return totalCandidates; }
    public List<AlbumPromptSuggestionAssetDto> getCandidates() { return candidates; }
}
