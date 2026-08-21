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

    public PageSettingsService(PageSettingsRepository repository, InstitutionRepository institutions, UserRepository users) {
        this.repository = repository;
        this.institutions = institutions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageSettingsDto get(UUID institutionId, JwtUserDetails actor) {
        authorize(institutionId, actor);
        return find(institutionId).map(PageSettingsDto::from)
                .orElse(new PageSettingsDto(institutionId, false, null, null, null));
    }

    @Transactional
    public PageSettingsDto update(UUID institutionId, UpdatePageSettingsRequestDto request, JwtUserDetails actor) {
        authorize(institutionId, actor);
        PageSettings settings = find(institutionId).orElseGet(PageSettings::new);
        if (institutionId != null && settings.getInstitution() == null) {
            settings.setInstitution(institutions.findById(institutionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found")));
        }
        settings.setWatermarkEnabled(request.watermarkEnabled());
        settings.setWatermarkText(trim(request.watermarkText()));
        settings.setFacebookPageId(trim(request.facebookPageId()));
        settings.setUpdatedBy(users.getReferenceById(actor.userId()));
        return PageSettingsDto.from(repository.save(settings));
    }

    private java.util.Optional<PageSettings> find(UUID institutionId) {
        return institutionId == null ? repository.findByInstitutionIsNull() : repository.findByInstitutionId(institutionId);
    }

    private void authorize(UUID institutionId, JwtUserDetails actor) {
        if ("super_administrator".equalsIgnoreCase(actor.role())) return;
        if ("administrator".equalsIgnoreCase(actor.role()) && institutionId != null
                && institutionId.equals(actor.institutionId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Page Settings access denied");
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
