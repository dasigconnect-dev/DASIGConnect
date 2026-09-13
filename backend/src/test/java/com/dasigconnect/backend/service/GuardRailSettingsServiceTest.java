package com.dasigconnect.backend.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.dasigconnect.backend.model.entity.PageSettings;
import com.dasigconnect.backend.repository.PageSettingsRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuardRailSettingsServiceTest {

    @Mock
    private PageSettingsRepository pageSettingsRepository;

    @InjectMocks
    private GuardRailSettingsService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultEnforced", true);
    }

    @Test
    void enforced_readsTheNoInstitutionRow() {
        PageSettings global = new PageSettings();
        global.setGuardrailsEnforced(false);
        when(pageSettingsRepository.findByInstitutionIsNull()).thenReturn(Optional.of(global));

        assertThat(service.enforced()).isFalse();
    }

    @Test
    void enforced_fallsBackToConfigDefault_whenNoGlobalRow() {
        when(pageSettingsRepository.findByInstitutionIsNull()).thenReturn(Optional.empty());

        assertThat(service.enforced()).isTrue();
    }
}
