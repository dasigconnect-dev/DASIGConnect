package com.dasigconnect.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class TenantScopeService {

    @PersistenceContext
    private EntityManager entityManager;

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
