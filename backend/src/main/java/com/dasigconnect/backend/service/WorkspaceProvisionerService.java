package com.dasigconnect.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.model.entity.Institution;

@Service
public class WorkspaceProvisionerService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceProvisionerService.class);

    private final TenantScopeService tenantScopeService;

    public WorkspaceProvisionerService(TenantScopeService tenantScopeService) {
        this.tenantScopeService = tenantScopeService;
    }

    @Transactional
    public void provision(Institution institution) {
        log.info("Provisioning workspace for institution: {} ({})",
                institution.getName(), institution.getId());

        tenantScopeService.bindTenantScope(institution.getId(), "administrator");

        log.info("Workspace provisioned successfully for institution {}", institution.getId());
    }
}
