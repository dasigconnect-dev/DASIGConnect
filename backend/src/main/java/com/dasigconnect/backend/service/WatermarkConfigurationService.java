package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationRequestDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.WatermarkConfigurationRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WatermarkConfigurationService {

    private final WatermarkConfigurationRepository repository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public WatermarkConfigurationService(
            WatermarkConfigurationRepository repository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WatermarkConfigurationDto get(UUID institutionId, JwtUserDetails actor) {
        authorizeRead(actor);

        Optional<WatermarkConfiguration> defaultConfig = repository.findByInstitutionIsNull();
        return defaultConfig.map(cfg -> mapToDto(cfg, false, "DASIG Central Visayas (Global)"))
                .orElseGet(() -> createDefaultDto(null, "DASIG Central Visayas (Global)", false));
    }

    @Transactional
    public WatermarkConfigurationDto save(WatermarkConfigurationRequestDto request, JwtUserDetails actor) {
        authorizeWrite(actor);

        if (request.elements() != null && request.elements().size() > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Watermark can contain at most 3 elements");
        }

        WatermarkConfiguration config = repository.findByInstitutionIsNull().orElseGet(WatermarkConfiguration::new);
        config.setInstitution(null);

        config.setEnabled(request.enabled());
        config.setElementsJson(serializeElements(sanitizeElements(request.elements())));
        config.setUpdatedBy(actor.email());

        WatermarkConfiguration saved = repository.save(config);
        String instName = "DASIG Central Visayas (Global)";

        try {
            User user = actor != null && actor.userId() != null ? userRepository.findById(actor.userId()).orElse(null) : null;
            Map<String, Object> meta = Map.of(
                    "institutionName", instName,
                    "enabled", saved.isEnabled(),
                    "elementsCount", request.elements() != null ? request.elements().size() : 0
            );
            auditLogService.record(user, "WATERMARK_CONFIG_UPDATED", null, null, saved.getId(), meta);
        } catch (Exception ignored) {}

        return mapToDto(saved, false, instName);
    }

    private void authorizeRead(JwtUserDetails actor) {
        // The watermark is a single global configuration that every role needs
        // in order to render post previews — any authenticated user may read it.
        if (actor != null) return;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    private void authorizeWrite(JwtUserDetails actor) {
        if (actor != null && "admin".equalsIgnoreCase(actor.role())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Watermark configuration write access denied");
    }

    private WatermarkConfigurationDto mapToDto(WatermarkConfiguration entity, boolean isOverride, String institutionName) {
        return new WatermarkConfigurationDto(
                entity.getId(),
                entity.getInstitution() != null ? entity.getInstitution().getId() : null,
                institutionName,
                entity.isEnabled(),
                isOverride,
                parseElements(entity.getElementsJson()),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }

    private WatermarkConfigurationDto createDefaultDto(UUID institutionId, String institutionName, boolean isOverride) {
        List<WatermarkElementDto> defaultElements = new ArrayList<>();

        WatermarkElementDto logo = new WatermarkElementDto();
        logo.setId("default-logo");
        logo.setType("image");
        logo.setXPercent(78.0);
        logo.setYPercent(82.0);
        logo.setWidthPercent(18.0);
        logo.setHeightPercent(14.0);
        logo.setOpacity(0.9);
        logo.setImageUrl("/dasig-logo.png");
        defaultElements.add(logo);

        WatermarkElementDto text = new WatermarkElementDto();
        text.setId("default-text");
        text.setType("text");
        text.setXPercent(55.0);
        text.setYPercent(92.0);
        text.setWidthPercent(40.0);
        text.setHeightPercent(6.0);
        text.setText("@DASIGCentralVisayas");
        text.setTextColor("#FFFFFF");
        text.setFontSizePercent(2.8);
        text.setFontWeight("700");
        text.setOpacity(0.9);
        defaultElements.add(text);

        return new WatermarkConfigurationDto(
                null,
                institutionId,
                institutionName,
                true,
                isOverride,
                defaultElements,
                null,
                null
        );
    }

    private List<WatermarkElementDto> sanitizeElements(List<WatermarkElementDto> raw) {
        if (raw == null) return Collections.emptyList();
        List<WatermarkElementDto> sanitized = new ArrayList<>();
        for (WatermarkElementDto el : raw) {
            if (el == null || el.getType() == null) continue;
            if (el.getId() == null || el.getId().isBlank()) {
                el.setId(UUID.randomUUID().toString());
            }
            el.setXPercent(clamp(el.getXPercent(), 0.0, 100.0));
            el.setYPercent(clamp(el.getYPercent(), 0.0, 100.0));
            el.setWidthPercent(clamp(el.getWidthPercent(), 1.0, 100.0));
            el.setHeightPercent(clamp(el.getHeightPercent(), 1.0, 100.0));
            el.setOpacity(clamp(el.getOpacity(), 0.05, 1.0));
            sanitized.add(el);
            if (sanitized.size() >= 3) break;
        }
        return sanitized;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<WatermarkElementDto> parseElements(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<WatermarkElementDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String serializeElements(List<WatermarkElementDto> elements) {
        try {
            return objectMapper.writeValueAsString(elements != null ? elements : Collections.emptyList());
        } catch (Exception e) {
            return "[]";
        }
    }
}
