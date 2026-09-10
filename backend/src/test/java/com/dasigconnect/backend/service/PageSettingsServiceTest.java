package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.UpdatePageSettingsRequestDto;
import com.dasigconnect.backend.model.entity.PageSettings;
import com.dasigconnect.backend.repository.InstitutionRepository;
import com.dasigconnect.backend.repository.PageSettingsRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageSettingsServiceTest {
    @Mock PageSettingsRepository repository;
    @Mock InstitutionRepository institutions;
    @Mock UserRepository users;
    @Mock GuardRailSettingsService guardRailSettings;
    @InjectMocks PageSettingsService service;

    @Test
    void contributorCannotReadPageSettings() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "user@example.com", "contributor", UUID.randomUUID());
        assertThatThrownBy(() -> service.get(actor.institutionId(), actor))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void moderatorCannotReadOtherInstitutionSettings() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "admin@example.com", "moderator", UUID.randomUUID());
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), actor))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void adminCanReadNetworkSettings() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "super@example.com", "admin", null);
        when(repository.findByInstitutionIsNull()).thenReturn(Optional.empty());
        service.get(null, actor);
    }

    @Test
    void networkUpdate_appliesGuardrailsToggle() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "admin@example.com", "admin", null);
        when(repository.findByInstitutionIsNull()).thenReturn(Optional.empty());
        when(users.getReferenceById(actor.userId())).thenReturn(new com.dasigconnect.backend.model.entity.User());
        when(repository.save(any(PageSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.update(null, new UpdatePageSettingsRequestDto(null, false), actor);

        assertThat(result.guardrailsEnforced()).isFalse();
    }

    @Test
    void perInstitutionUpdate_ignoresGuardrailsToggle() {
        UUID institutionId = UUID.randomUUID();
        var actor = new JwtUserDetails(UUID.randomUUID(), "admin@example.com", "admin", null);
        var inst = new com.dasigconnect.backend.model.entity.Institution();
        inst.setId(institutionId);
        PageSettings existing = new PageSettings();
        existing.setInstitution(inst);
        existing.setGuardrailsEnforced(true);
        when(repository.findByInstitutionId(institutionId)).thenReturn(Optional.of(existing));
        when(users.getReferenceById(actor.userId())).thenReturn(new com.dasigconnect.backend.model.entity.User());
        when(repository.save(any(PageSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.update(institutionId, new UpdatePageSettingsRequestDto(null, false), actor);

        // The switch is network-wide only — a per-institution PUT leaves it alone.
        assertThat(result.guardrailsEnforced()).isTrue();
    }
}
