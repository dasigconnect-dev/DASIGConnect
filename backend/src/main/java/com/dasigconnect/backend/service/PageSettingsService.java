package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.PageSettingsDto;
import com.dasigconnect.backend.model.dto.settings.UpdatePageSettingsRequestDto;
import com.dasigconnect.backend.model.entity.PageSettings;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.PageSettingsRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PageSettingsService {
    private final PageSettingsRepository repository;
    private final InstitutionRepository institutions;
    private final UserRepository users;
    private final GuardRailSettingsService guardRailSettings;

    public PageSettingsService(PageSettingsRepository repository, InstitutionRepository institutions,
            UserRepository users, GuardRailSettingsService guardRailSettings) {
        this.repository = repository;
        this.institutions = institutions;
        this.users = users;
        this.guardRailSettings = guardRailSettings;
    }

    @Transactional(readOnly = true)
    public PageSettingsDto get(UUID institutionId, JwtUserDetails actor) {
        authorize(institutionId, actor);
        return find(institutionId).map(PageSettingsDto::from)
                .orElse(new PageSettingsDto(
                        institutionId, 
                        guardRailSettings.enforced(), 
                        guardRailSettings.postingWindowStartHour(), 
                        guardRailSettings.postingWindowEndHour(), 
                        guardRailSettings.conflictBufferMinutes(),
                        guardRailSettings.minimumLeadTimeHours(),
                        guardRailSettings.maximumLeadTimeDays(),
                        guardRailSettings.perInstitutionActiveQuota(),
                        guardRailSettings.dailyVolumeCap(),
                        null));
    }

    @Transactional
    public PageSettingsDto update(UUID institutionId, UpdatePageSettingsRequestDto request, JwtUserDetails actor) {
        authorize(institutionId, actor);
        PageSettings settings = find(institutionId).orElseGet(PageSettings::new);
        if (institutionId != null && settings.getInstitution() == null) {
            settings.setInstitution(institutions.findById(institutionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found")));
        }
        // The guard-rail switch is network-wide — only honoured on the
        // no-institution row; ignored on per-institution Page Settings.
        if (institutionId == null) {
            validateNetworkGuardRailSettings(request, settings);
            if (request.guardrailsEnforced() != null) {
                settings.setGuardrailsEnforced(request.guardrailsEnforced());
            }
            if (request.postingWindowStartHour() != null) {
                settings.setPostingWindowStartHour(request.postingWindowStartHour());
            }
            if (request.postingWindowEndHour() != null) {
                settings.setPostingWindowEndHour(request.postingWindowEndHour());
            }
            if (request.conflictBufferMinutes() != null) {
                settings.setConflictBufferMinutes(request.conflictBufferMinutes());
            }
            if (request.minimumLeadTimeHours() != null) {
                settings.setMinimumLeadTimeHours(request.minimumLeadTimeHours());
            }
            if (request.maximumLeadTimeDays() != null) {
                settings.setMaximumLeadTimeDays(request.maximumLeadTimeDays());
            }
            if (request.perInstitutionActiveQuota() != null) {
                settings.setPerInstitutionActiveQuota(request.perInstitutionActiveQuota());
            }
            if (request.dailyVolumeCap() != null) {
                settings.setDailyVolumeCap(request.dailyVolumeCap());
            }
        }
        settings.setUpdatedBy(users.getReferenceById(actor.userId()));
        return PageSettingsDto.from(repository.save(settings));
    }

    private void validateNetworkGuardRailSettings(
            UpdatePageSettingsRequestDto request, PageSettings current) {
        int startHour = request.postingWindowStartHour() != null
                ? request.postingWindowStartHour() : current.getPostingWindowStartHour();
        int endHour = request.postingWindowEndHour() != null
                ? request.postingWindowEndHour() : current.getPostingWindowEndHour();

        if (startHour < 0 || startHour > 23) {
            throw badRequest("Start hour must be between 0 and 23.");
        }
        if (endHour < 0 || endHour > 23) {
            throw badRequest("End hour must be between 0 and 23.");
        }
        if (startHour >= endHour) {
            throw badRequest("Start hour must be strictly before the end hour.");
        }
        requireNonNegative(request.conflictBufferMinutes(), "Conflict buffer minutes");
        requireNonNegative(request.minimumLeadTimeHours(), "Minimum lead time hours");
        requirePositive(request.maximumLeadTimeDays(), "Maximum lead time days");
        requirePositive(request.perInstitutionActiveQuota(), "Per-institution active quota");
        requirePositive(request.dailyVolumeCap(), "Daily volume cap");
    }

    private void requireNonNegative(Integer value, String label) {
        if (value != null && value < 0) {
            throw badRequest(label + " must be zero or greater.");
        }
    }

    private void requirePositive(Integer value, String label) {
        if (value != null && value <= 0) {
            throw badRequest(label + " must be greater than zero.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private java.util.Optional<PageSettings> find(UUID institutionId) {
        return institutionId == null ? repository.findByInstitutionIsNull() : repository.findByInstitutionId(institutionId);
    }

    private void authorize(UUID institutionId, JwtUserDetails actor) {
        if (actor != null && "admin".equalsIgnoreCase(actor.role())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Page Settings access denied");
    }
}

