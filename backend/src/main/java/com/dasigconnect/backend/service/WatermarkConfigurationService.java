package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationRequestDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import com.dasigconnect.backend.repository.InstitutionRepository;
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
    private final InstitutionRepository institutions;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public WatermarkConfigurationService(
            WatermarkConfigurationRepository repository,
            InstitutionRepository institutions,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.institutions = institutions;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WatermarkConfigurationDto get(UUID institutionId, JwtUserDetails actor) {
        if (institutionId == null && "administrator".equalsIgnoreCase(actor.role()) && actor.institutionId() != null) {
            institutionId = actor.institutionId();
        }
        authorizeRead(institutionId, actor);

        if (institutionId != null) {
            Institution institution = institutions.findById(institutionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));

            Optional<WatermarkConfiguration> customConfig = repository.findByInstitutionId(institutionId);
            if (customConfig.isPresent()) {
                return mapToDto(customConfig.get(), true, institution.getName());
            }

            // Fallback to network-wide default
            Optional<WatermarkConfiguration> defaultConfig = repository.findByInstitutionIsNull();
            if (defaultConfig.isPresent()) {
                WatermarkConfiguration cfg = defaultConfig.get();
                return new WatermarkConfigurationDto(
                        cfg.getId(),
                        institutionId,
                        institution.getName(),
                        cfg.isEnabled(),
                        false, // isOverride is false because it's inheriting default
                        parseElements(cfg.getElementsJson()),
                        cfg.getUpdatedAt(),
                        cfg.getUpdatedBy()
                );
            }

            return createDefaultDto(institutionId, institution.getName(), false);
        }

        // Network-wide default requested
        Optional<WatermarkConfiguration> defaultConfig = repository.findByInstitutionIsNull();
        return defaultConfig.map(cfg -> mapToDto(cfg, false, "DASIG Central Visayas (Default)"))
                .orElseGet(() -> createDefaultDto(null, "DASIG Central Visayas (Default)", false));
    }

    @Transactional
    public WatermarkConfigurationDto save(WatermarkConfigurationRequestDto request, JwtUserDetails actor) {
        UUID institutionId = request.institutionId();
        if (institutionId == null && "administrator".equalsIgnoreCase(actor.role()) && actor.institutionId() != null) {
            institutionId = actor.institutionId();
        }
        authorizeWrite(institutionId, actor);

        if (request.elements() != null && request.elements().size() > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Watermark can contain at most 3 elements");
        }

        WatermarkConfiguration config;
        Institution institution = null;

        if (institutionId != null) {
            institution = institutions.findById(institutionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found"));
            config = repository.findByInstitutionId(institutionId).orElseGet(WatermarkConfiguration::new);
            config.setInstitution(institution);
        } else {
            config = repository.findByInstitutionIsNull().orElseGet(WatermarkConfiguration::new);
            config.setInstitution(null);
        }

        config.setEnabled(request.enabled());
        config.setElementsJson(serializeElements(sanitizeElements(request.elements())));
        config.setUpdatedBy(actor.email());

        WatermarkConfiguration saved = repository.save(config);
        boolean isOverride = institutionId != null;
        String instName = institution != null ? institution.getName() : "DASIG Central Visayas (Default)";

        try {
            User user = actor != null && actor.userId() != null ? userRepository.findById(actor.userId()).orElse(null) : null;
            Map<String, Object> meta = Map.of(
                    "institutionName", instName,
                    "enabled", saved.isEnabled(),
                    "elementsCount", request.elements() != null ? request.elements().size() : 0
            );
            auditLogService.record(user, "WATERMARK_CONFIG_UPDATED", null, null, saved.getId(), meta);
        } catch (Exception ignored) {}

        return mapToDto(saved, isOverride, instName);
    }

    @Transactional
    public void deleteOverride(UUID institutionId, JwtUserDetails actor) {
        if (institutionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institution ID is required to remove override");
        }
        authorizeWrite(institutionId, actor);

        repository.findByInstitutionId(institutionId).ifPresent(config -> {
            repository.delete(config);
            try {
                User user = actor != null && actor.userId() != null ? userRepository.findById(actor.userId()).orElse(null) : null;
                Map<String, Object> meta = Map.of("institutionId", institutionId.toString(), "action", "RESET_TO_DEFAULT");
                auditLogService.record(user, "WATERMARK_OVERRIDE_REMOVED", null, null, institutionId, meta);
            } catch (Exception ignored) {}
        });
    }

    private void authorizeRead(UUID institutionId, JwtUserDetails actor) {
        if ("super_administrator".equalsIgnoreCase(actor.role())) return;
        if ("administrator".equalsIgnoreCase(actor.role())) {
            if (institutionId == null || institutionId.equals(actor.institutionId())) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Watermark configuration read access denied");
    }

    private void authorizeWrite(UUID institutionId, JwtUserDetails actor) {
        if ("super_administrator".equalsIgnoreCase(actor.role())) return;
        if ("administrator".equalsIgnoreCase(actor.role())) {
            if (institutionId != null && institutionId.equals(actor.institutionId())) return;
            if (institutionId == null && actor.institutionId() == null) return;
        }
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
