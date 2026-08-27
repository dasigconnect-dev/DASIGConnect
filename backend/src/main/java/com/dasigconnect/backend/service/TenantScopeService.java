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
    public void bindTenantScope(UUID userId, UUID institutionId, String role) {
        // RLS policies grant the tenant-isolation bypass to 'administrator' only.
        // A super administrator is a network-wide administrator, so bind the same
        // scope role for row visibility (service-layer checks still apply).
        String scopeRole = "super_administrator".equalsIgnoreCase(role) ? "administrator" : role;
        // app.current_user_id lets the media_assets RLS policy expose a user's own
        // STAGED uploads (which have no institution). See V73__media_asset_staging.sql.
        setLocal("app.current_user_id", userId == null ? "" : userId.toString());
        setLocal("app.current_institution_id", institutionId == null ? "" : institutionId.toString());
        setLocal("app.current_role", scopeRole == null ? "" : scopeRole);
    }

    private void setLocal(String key, String value) {
        entityManager
                .createNativeQuery("SELECT set_config(:key, :value, false)")
                .setParameter("key", key)
                .setParameter("value", value)
                .getSingleResult();
    }
}
