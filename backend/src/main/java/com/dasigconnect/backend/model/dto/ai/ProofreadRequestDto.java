package com.dasigconnect.backend.model.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Text to proofread. {@code originalText} is optional: when present (a
 * reviewer's edit of a contributor's caption), only problems the edit
 * introduced are reported, plus any change in meaning.
 */
public class ProofreadRequestDto {

    /** Captions cap at 3000 code points; allow headroom for surrogate pairs. */
    @NotBlank
    @Size(max = 6000)
    private String text;

    @Size(max = 6000)
    private String originalText;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
}
