package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.template.PostTemplateDto;
import com.dasigconnect.backend.model.dto.template.PostTemplateRequestDto;
import com.dasigconnect.backend.model.entity.PostTemplate;
import com.dasigconnect.backend.repository.PostTemplateRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostTemplateService {

    private final PostTemplateRepository repository;

    public PostTemplateService(PostTemplateRepository repository) {
        this.repository = repository;
    }

    public List<PostTemplateDto> list(JwtUserDetails user) {
        return repository.findByOwnerUserIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .map(PostTemplateDto::from)
                .toList();
    }

    public PostTemplateDto create(PostTemplateRequestDto dto, JwtUserDetails user) {
        String name = normalizeRequired(dto.getName(), "Template name is required");
        if (repository.existsByOwnerUserIdAndNameIgnoreCase(user.userId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A template with this name already exists.");
        }

        PostTemplate template = new PostTemplate();
        template.setOwnerUserId(user.userId());
        template.setInstitutionId(dto.getInstitutionId() != null ? dto.getInstitutionId() : user.institutionId());
        template.setName(name);
        template.setTarget(normalizeOptional(dto.getTarget(), "Saved from submission"));
        template.setCategory(normalizeOptional(dto.getCategory(), "Custom"));
        template.setCaption(normalizeRequired(dto.getCaption(), "Caption is required"));
        template.setTags(normalizeTags(dto.getTags()));
        template.setSourceSubmissionId(dto.getSourceSubmissionId());
        return PostTemplateDto.from(repository.save(template));
    }

    public void delete(java.util.UUID templateId, JwtUserDetails user) {
        PostTemplate template = repository.findByIdAndOwnerUserId(templateId, user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found."));
        repository.delete(template);
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeTags(List<String> tags) {
        if (tags == null) return null;
        String joined = tags.stream()
                .map(tag -> tag == null ? "" : tag.trim().replace(",", ""))
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .limit(8)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return joined.isBlank() ? null : joined;
    }
}
