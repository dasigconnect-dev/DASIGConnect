package com.dasigconnect.backend.model.dto.institution;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;

class InstitutionDtoTest {

    @Test
    @DisplayName("from() maps Institution entity fields correctly")
    void from_mapsEntityFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName("Cebu Institute of Technology - University");
        institution.setCode("CIT-U");
        institution.setStatus(InstitutionStatus.active);

        InstitutionDto dto = InstitutionDto.from(institution);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("Cebu Institute of Technology - University");
        assertThat(dto.getInstitutionCode()).isEqualTo("CIT-U");
        assertThat(dto.getStatus()).isEqualTo(InstitutionStatus.active);
    }

    @Test
    @DisplayName("from() maps INACTIVE status correctly")
    void from_mapsInactiveStatus() {
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());
        institution.setName("Test");
        institution.setCode("TEST");
        institution.setStatus(InstitutionStatus.inactive);

        InstitutionDto dto = InstitutionDto.from(institution);

        assertThat(dto.getStatus()).isEqualTo(InstitutionStatus.inactive);
    }

    @Test
    @DisplayName("from() maps PENDING status correctly")
    void from_mapsPendingStatus() {
        Institution institution = new Institution();
        institution.setId(UUID.randomUUID());
        institution.setName("Test");
        institution.setCode("TEST");
        institution.setStatus(InstitutionStatus.pending);

        InstitutionDto dto = InstitutionDto.from(institution);

        assertThat(dto.getStatus()).isEqualTo(InstitutionStatus.pending);
    }

    @Test
    @DisplayName("CreateInstitutionRequest getters return correct values")
    void createRequest_gettersReturnCorrectValues() {
        CreateInstitutionRequest req = new CreateInstitutionRequest(
                "Ateneo de Davao University", "ADDU", "addu.edu.ph");

        assertThat(req.getName()).isEqualTo("Ateneo de Davao University");
        assertThat(req.getInstitutionCode()).isEqualTo("ADDU");
    }

    @Test
    @DisplayName("CreateInstitutionRequest setters update values")
    void createRequest_settersUpdateValues() {
        CreateInstitutionRequest req = new CreateInstitutionRequest();
        req.setName("University of San Carlos");
        req.setInstitutionCode("USC");

        assertThat(req.getName()).isEqualTo("University of San Carlos");
        assertThat(req.getInstitutionCode()).isEqualTo("USC");
    }
}
