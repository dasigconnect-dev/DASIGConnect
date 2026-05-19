package com.dasigconnect.backend.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantScopeService {

    private final EntityManager entityManager;

    public TenantScopeService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void bindTenantScope(UUID institutionId, String role) {
        setLocal("app.current_institution_id", institutionId == null ? "" : institutionId.toString());
        setLocal("app.current_role", role == null ? "" : role);
    }

    private void setLocal(String key, String value) {
        entityManager
                .createNativeQuery("SELECT set_config(:key, :value, true)")
                .setParameter("key", key)
                .setParameter("value", value)
                .getSingleResult();
    }
}
