package com.dasigconnect.backend.model.dto.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class CaptionRequestDto {
    @NotNull
    private UUID submissionId;

    /** The contributor's current draft caption. Null or blank = generate from scratch. */
    @Size(max = 500)
    private String existingCaption;

    /** Optional contributor instructions for tone, focus, length, or details to include. */
    @Size(max = 280)
    private String prompt;

    /** Selected caption style. Null defaults to professional. */
    @Pattern(regexp = "professional|community|energetic")
    private String tone;

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }

    public String getExistingCaption() { return existingCaption; }
    public void setExistingCaption(String existingCaption) { this.existingCaption = existingCaption; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
}
