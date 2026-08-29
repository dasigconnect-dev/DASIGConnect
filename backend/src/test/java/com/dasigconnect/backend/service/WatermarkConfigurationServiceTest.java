package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkConfigurationRequestDto;
import com.dasigconnect.backend.model.dto.settings.WatermarkElementDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.WatermarkConfiguration;
import com.dasigconnect.backend.repository.InstitutionRepository;
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
    private InstitutionRepository institutions;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    private ObjectMapper objectMapper;
    private WatermarkConfigurationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new WatermarkConfigurationService(repository, institutions, userRepository, auditLogService, objectMapper);
    }

    @Test
    void contributorCannotAccessWatermarkConfig() {
        var actor = new JwtUserDetails(UUID.randomUUID(), "contrib@example.com", "contributor", UUID.randomUUID());
        assertThatThrownBy(() -> service.get(null, actor))
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
    void institutionFallsBackToNetworkDefaultWhenNoOverride() {
        UUID instId = UUID.randomUUID();
        var actor = new JwtUserDetails(UUID.randomUUID(), "admin@example.com", "admin", instId);

        Institution inst = new Institution();
        inst.setId(instId);
        inst.setName("CIT University");
        when(institutions.findById(instId)).thenReturn(Optional.of(inst));
        when(repository.findByInstitutionId(instId)).thenReturn(Optional.empty());

        WatermarkConfiguration defaultConfig = new WatermarkConfiguration();
        defaultConfig.setId(UUID.randomUUID());
        defaultConfig.setEnabled(true);
        defaultConfig.setElementsJson("[{\"id\":\"logo\",\"type\":\"image\",\"xPercent\":80,\"yPercent\":80,\"widthPercent\":15,\"heightPercent\":15}]");
        when(repository.findByInstitutionIsNull()).thenReturn(Optional.of(defaultConfig));

        WatermarkConfigurationDto dto = service.get(instId, actor);

        assertThat(dto).isNotNull();
        assertThat(dto.institutionId()).isEqualTo(instId);
        assertThat(dto.institutionName()).isEqualTo("CIT University");
        assertThat(dto.isOverride()).isFalse();
        assertThat(dto.elements()).hasSize(1);
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
