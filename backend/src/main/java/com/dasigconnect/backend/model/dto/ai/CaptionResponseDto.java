package com.dasigconnect.backend.model.dto.ai;

import java.util.List;
import java.util.UUID;

public class CaptionResponseDto {
    private UUID submissionId;
    private List<CaptionVariantDto> variants;

    public CaptionResponseDto() {}

    public CaptionResponseDto(UUID submissionId, List<CaptionVariantDto> variants) {
        this.submissionId = submissionId;
        this.variants = variants;
    }

    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }
    public List<CaptionVariantDto> getVariants() { return variants; }
    public void setVariants(List<CaptionVariantDto> variants) { this.variants = variants; }
}
