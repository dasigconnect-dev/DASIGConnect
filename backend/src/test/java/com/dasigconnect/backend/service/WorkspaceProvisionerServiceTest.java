package com.dasigconnect.backend.service;

import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dasigconnect.backend.model.entity.Institution;

@ExtendWith(MockitoExtension.class)
class WorkspaceProvisionerServiceTest {

    @Mock
    private TenantScopeService tenantScopeService;

    private WorkspaceProvisionerService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceProvisionerService(tenantScopeService);
    }

    @Test
    void provision_bindsTenantScopeForAdmin() {
        UUID institutionId = UUID.randomUUID();
        Institution institution = new Institution();
        institution.setId(institutionId);
        institution.setName("Cebu Institute of Technology - University");

        service.provision(institution);

        verify(tenantScopeService).bindTenantScope(null, institutionId, "admin");
    }
}
