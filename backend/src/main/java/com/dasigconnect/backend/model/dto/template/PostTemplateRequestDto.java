package com.dasigconnect.backend.model.dto.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class PostTemplateRequestDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String target;

    @Size(max = 100)
    private String category;

    @NotBlank
    @Size(max = 3000)
    private String caption;

    private List<@Size(max = 50) String> tags;
    private UUID sourceSubmissionId;
    private UUID institutionId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public UUID getSourceSubmissionId() { return sourceSubmissionId; }
    public void setSourceSubmissionId(UUID sourceSubmissionId) { this.sourceSubmissionId = sourceSubmissionId; }
    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
}
