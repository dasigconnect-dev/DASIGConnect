package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.UpdatePageSettingsRequestDto;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PageSettingsServiceTest {
    @Mock PageSettingsRepository repository;
    @Mock InstitutionRepository institutions;
    @Mock UserRepository users;
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
        org.mockito.Mockito.when(repository.findByInstitutionIsNull()).thenReturn(Optional.empty());
        service.get(null, actor);
    }
}
