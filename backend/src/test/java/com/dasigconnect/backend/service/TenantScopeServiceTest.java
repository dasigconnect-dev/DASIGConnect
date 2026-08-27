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
    private Query userQuery;

    @Mock
    private Query institutionQuery;

    @Mock
    private Query roleQuery;

    @Test
    void bindTenantScopeSetsUserInstitutionAndRoleSessionConfig() {
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        when(entityManager.createNativeQuery("SELECT set_config(:key, :value, false)"))
                .thenReturn(userQuery, institutionQuery, roleQuery);
        when(userQuery.setParameter("key", "app.current_user_id")).thenReturn(userQuery);
        when(userQuery.setParameter("value", userId.toString())).thenReturn(userQuery);
        when(institutionQuery.setParameter("key", "app.current_institution_id")).thenReturn(institutionQuery);
        when(institutionQuery.setParameter("value", institutionId.toString())).thenReturn(institutionQuery);
        when(roleQuery.setParameter("key", "app.current_role")).thenReturn(roleQuery);
        when(roleQuery.setParameter("value", "contributor")).thenReturn(roleQuery);

        TenantScopeService service = tenantScopeService();

        service.bindTenantScope(userId, institutionId, "contributor");

        InOrder inOrder = inOrder(entityManager, userQuery, institutionQuery, roleQuery);
        inOrder.verify(entityManager).createNativeQuery("SELECT set_config(:key, :value, false)");
        inOrder.verify(userQuery).setParameter("key", "app.current_user_id");
        inOrder.verify(userQuery).setParameter("value", userId.toString());
        inOrder.verify(userQuery).getSingleResult();
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
                .thenReturn(userQuery, institutionQuery, roleQuery);
        when(userQuery.setParameter("key", "app.current_user_id")).thenReturn(userQuery);
        when(userQuery.setParameter("value", "")).thenReturn(userQuery);
        when(institutionQuery.setParameter("key", "app.current_institution_id")).thenReturn(institutionQuery);
        when(institutionQuery.setParameter("value", "")).thenReturn(institutionQuery);
        when(roleQuery.setParameter("key", "app.current_role")).thenReturn(roleQuery);
        when(roleQuery.setParameter("value", "")).thenReturn(roleQuery);

        TenantScopeService service = tenantScopeService();

        service.bindTenantScope(null, null, null);

        InOrder inOrder = inOrder(entityManager, userQuery, institutionQuery, roleQuery);
        inOrder.verify(userQuery).setParameter("key", "app.current_user_id");
        inOrder.verify(userQuery).setParameter("value", "");
        inOrder.verify(userQuery).getSingleResult();
        inOrder.verify(institutionQuery).setParameter("key", "app.current_institution_id");
        inOrder.verify(institutionQuery).setParameter("value", "");
        inOrder.verify(institutionQuery).getSingleResult();
        inOrder.verify(roleQuery).setParameter("key", "app.current_role");
        inOrder.verify(roleQuery).setParameter("value", "");
        inOrder.verify(roleQuery).getSingleResult();
    }

    @Test
    void bindTenantScopeMapsSuperAdministratorToAdministratorForRls() {
        when(entityManager.createNativeQuery("SELECT set_config(:key, :value, false)"))
                .thenReturn(userQuery, institutionQuery, roleQuery);
        when(userQuery.setParameter("key", "app.current_user_id")).thenReturn(userQuery);
        when(userQuery.setParameter("value", "")).thenReturn(userQuery);
        when(institutionQuery.setParameter("key", "app.current_institution_id")).thenReturn(institutionQuery);
        when(institutionQuery.setParameter("value", "")).thenReturn(institutionQuery);
        when(roleQuery.setParameter("key", "app.current_role")).thenReturn(roleQuery);
        when(roleQuery.setParameter("value", "administrator")).thenReturn(roleQuery);

        TenantScopeService service = tenantScopeService();

        service.bindTenantScope(null, null, "super_administrator");

        InOrder inOrder = inOrder(entityManager, roleQuery);
        inOrder.verify(roleQuery).setParameter("key", "app.current_role");
        inOrder.verify(roleQuery).setParameter("value", "administrator");
        inOrder.verify(roleQuery).getSingleResult();
    }

    private TenantScopeService tenantScopeService() {
        TenantScopeService service = new TenantScopeService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
    }
}
