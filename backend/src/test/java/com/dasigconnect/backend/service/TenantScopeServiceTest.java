package com.dasigconnect.backend.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TenantScopeServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query institutionQuery;

    @Mock
    private Query roleQuery;

    @Test
    void bindTenantScopeSetsInstitutionAndRoleSessionConfig() {
        UUID institutionId = UUID.randomUUID();
        when(entityManager.createNativeQuery("SELECT set_config(:key, :value, false)"))
                .thenReturn(institutionQuery, roleQuery);
        when(institutionQuery.setParameter("key", "app.current_institution_id")).thenReturn(institutionQuery);
        when(institutionQuery.setParameter("value", institutionId.toString())).thenReturn(institutionQuery);
        when(roleQuery.setParameter("key", "app.current_role")).thenReturn(roleQuery);
        when(roleQuery.setParameter("value", "contributor")).thenReturn(roleQuery);

        TenantScopeService service = tenantScopeService();

        service.bindTenantScope(institutionId, "contributor");

        InOrder inOrder = inOrder(entityManager, institutionQuery, roleQuery);
        inOrder.verify(entityManager).createNativeQuery("SELECT set_config(:key, :value, false)");
        inOrder.verify(institutionQuery).setParameter("key", "app.current_institution_id");
        inOrder.verify(institutionQuery).setParameter("value", institutionId.toString());
        inOrder.verify(institutionQuery).getSingleResult();
        inOrder.verify(entityManager).createNativeQuery("SELECT set_config(:key, :value, false)");
        inOrder.verify(roleQuery).setParameter("key", "app.current_role");
        inOrder.verify(roleQuery).setParameter("value", "contributor");
        inOrder.verify(roleQuery).getSingleResult();
    }

    @Test
    void bindTenantScopeUsesEmptyStringsForNullValues() {
        when(entityManager.createNativeQuery("SELECT set_config(:key, :value, false)"))
                .thenReturn(institutionQuery, roleQuery);
        when(institutionQuery.setParameter("key", "app.current_institution_id")).thenReturn(institutionQuery);
        when(institutionQuery.setParameter("value", "")).thenReturn(institutionQuery);
        when(roleQuery.setParameter("key", "app.current_role")).thenReturn(roleQuery);
        when(roleQuery.setParameter("value", "")).thenReturn(roleQuery);

        TenantScopeService service = tenantScopeService();

        service.bindTenantScope(null, null);

        InOrder inOrder = inOrder(entityManager, institutionQuery, roleQuery);
        inOrder.verify(entityManager).createNativeQuery("SELECT set_config(:key, :value, false)");
        inOrder.verify(institutionQuery).setParameter("key", "app.current_institution_id");
        inOrder.verify(institutionQuery).setParameter("value", "");
        inOrder.verify(institutionQuery).getSingleResult();
        inOrder.verify(entityManager).createNativeQuery("SELECT set_config(:key, :value, false)");
        inOrder.verify(roleQuery).setParameter("key", "app.current_role");
        inOrder.verify(roleQuery).setParameter("value", "");
        inOrder.verify(roleQuery).getSingleResult();
    }

    private TenantScopeService tenantScopeService() {
        TenantScopeService service = new TenantScopeService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
    }
}
