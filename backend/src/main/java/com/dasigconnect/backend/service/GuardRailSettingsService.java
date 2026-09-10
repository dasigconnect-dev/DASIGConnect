package com.dasigconnect.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.PageSettings;
import com.dasigconnect.backend.repository.PageSettingsRepository;

/**
 * Single source of truth for whether scheduling guard rails are enforced.
 *
 * <p>Guard rails (±30-minute spacing, ≤6 posts/day network-wide, ≥2h lead
 * time, 8:00 AM–8:00 PM publish window) govern the one shared DASIG
 * publishing calendar, so this is a network-wide flag — it lives on the
 * no-institution {@code page_settings} row and an admin flips it from Page
 * Settings. When no such row exists yet, the {@code app.guardrails.enforced}
 * config value is the default.
 */
@Service
public class GuardRailSettingsService {

    private final PageSettingsRepository pageSettingsRepository;

    @Value("${app.guardrails.enforced:true}")
    private boolean defaultEnforced;

    public GuardRailSettingsService(PageSettingsRepository pageSettingsRepository) {
        this.pageSettingsRepository = pageSettingsRepository;
    }

    @Transactional(readOnly = true)
    public boolean enforced() {
        return pageSettingsRepository.findByInstitutionIsNull()
                .map(PageSettings::isGuardrailsEnforced)
                .orElse(defaultEnforced);
    }
}
