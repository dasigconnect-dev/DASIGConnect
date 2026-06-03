package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class AlbumPromptSuggestionRequestDto {

    @NotBlank
    @Size(max = 500)
    private String prompt;

    /** Required for administrators; ignored for institution-scoped users. */
    private UUID institutionId;

    @Min(1)
    @Max(60)
    private Integer limit;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
