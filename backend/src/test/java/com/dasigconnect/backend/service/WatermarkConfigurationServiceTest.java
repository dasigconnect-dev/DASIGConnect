package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationRequestDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.WatermarkConfigurationRepository;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatermarkConfigurationServiceTest {

    @Mock
    private WatermarkConfigurationRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    private ObjectMapper objectMapper;
    private WatermarkConfigurationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new WatermarkConfigurationService(repository, userRepository, auditLogService, objectMapper);
    }

    @Test
    void contributorCanReadGlobalWatermark() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "contrib@example.com", "contributor", UUID.randomUUID());
        when(repository.findByInstitutionIsNull()).thenReturn(Optional.empty());

        WatermarkConfigurationDto dto = service.get(null, actor);

        assertThat(dto).isNotNull();
        assertThat(dto.elements()).isNotEmpty();
    }

    @Test
    void unauthenticatedCannotReadWatermark() {
        assertThatThrownBy(() -> service.get(null, null))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void contributorCannotSaveWatermark() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "contrib@example.com", "contributor", UUID.randomUUID());
        var request = new WatermarkConfigurationRequestDto(null, true, new ArrayList<>());
        assertThatThrownBy(() -> service.save(request, actor))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void superAdminGetsNetworkDefaultWhenNoneSaved() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "super@example.com", "admin", null);
        when(repository.findByInstitutionIsNull()).thenReturn(Optional.empty());

        WatermarkConfigurationDto dto = service.get(null, actor);

        assertThat(dto).isNotNull();
        assertThat(dto.institutionId()).isNull();
        assertThat(dto.isOverride()).isFalse();
        assertThat(dto.elements()).isNotEmpty();
    }

    @Test
    void institutionRequestUsesGlobalWatermark() {
        UUID instId = UUID.randomUUID();
        var actor = new JwtUserDetails(UUID.randomUUID(), "admin@example.com", "admin", instId);

        WatermarkConfiguration defaultConfig = new WatermarkConfiguration();
        defaultConfig.setId(UUID.randomUUID());
        defaultConfig.setEnabled(true);
        defaultConfig.setElementsJson("[{\"id\":\"logo\",\"type\":\"image\",\"xPercent\":80,\"yPercent\":80,\"widthPercent\":15,\"heightPercent\":15}]");
        when(repository.findByInstitutionIsNull()).thenReturn(Optional.of(defaultConfig));

        WatermarkConfigurationDto dto = service.get(instId, actor);

        assertThat(dto).isNotNull();
        assertThat(dto.institutionId()).isNull();
        assertThat(dto.institutionName()).isEqualTo("DASIG Central Visayas (Global)");
        assertThat(dto.isOverride()).isFalse();
        assertThat(dto.elements()).hasSize(1);
        verify(repository).findByInstitutionIsNull();
    }

    @Test
    void saveWatermarkConfigurationPersistsElements() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "super@example.com", "admin", null);

        List<WatermarkElementDto> elements = new ArrayList<>();
        WatermarkElementDto el1 = new WatermarkElementDto();
        el1.setId("el1");
        el1.setType("image");
        el1.setXPercent(80.0);
        el1.setYPercent(80.0);
        el1.setWidthPercent(15.0);
        el1.setHeightPercent(15.0);
        elements.add(el1);

        WatermarkConfigurationRequestDto request = new WatermarkConfigurationRequestDto(null, true, elements);

        when(repository.findByInstitutionIsNull()).thenReturn(Optional.empty());
        when(repository.save(any(WatermarkConfiguration.class))).thenAnswer(inv -> inv.getArgument(0));

        WatermarkConfigurationDto result = service.save(request, actor);

        assertThat(result).isNotNull();
        assertThat(result.elements()).hasSize(1);
        verify(repository).save(any(WatermarkConfiguration.class));
    }

    @Test
    void rejectsMoreThan3Elements() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "super@example.com", "admin", null);

        List<WatermarkElementDto> elements = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            WatermarkElementDto el = new WatermarkElementDto();
            el.setType("text");
            elements.add(el);
        }

        WatermarkConfigurationRequestDto request = new WatermarkConfigurationRequestDto(null, true, elements);

        assertThatThrownBy(() -> service.save(request, actor))
                .isInstanceOf(ResponseStatusException.class);
    }
}
