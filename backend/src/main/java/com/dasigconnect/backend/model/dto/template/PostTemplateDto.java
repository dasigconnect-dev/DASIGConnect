package com.dasigconnect.backend.model.dto.template;

import com.dasigconnect.backend.model.entity.PostTemplate;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PostTemplateDto {
    private UUID id;
    private String name;
    private String target;
    private String category;
    private String caption;
    private List<String> tags;
    private UUID sourceSubmissionId;
    private Instant createdAt;

    public static PostTemplateDto from(PostTemplate template) {
        PostTemplateDto dto = new PostTemplateDto();
        dto.id = template.getId();
        dto.name = template.getName();
        dto.target = template.getTarget();
        dto.category = template.getCategory();
        dto.caption = template.getCaption();
        dto.tags = template.getTags() == null || template.getTags().isBlank()
                ? List.of()
                : Arrays.stream(template.getTags().split(","))
                        .map(String::trim)
                        .filter(tag -> !tag.isEmpty())
                        .toList();
        dto.sourceSubmissionId = template.getSourceSubmissionId();
        dto.createdAt = template.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getTarget() { return target; }
    public String getCategory() { return category; }
    public String getCaption() { return caption; }
    public List<String> getTags() { return tags; }
    public UUID getSourceSubmissionId() { return sourceSubmissionId; }
    public Instant getCreatedAt() { return createdAt; }
}
