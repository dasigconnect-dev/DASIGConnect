package com.dasigconnect.backend.service;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.exception.InstitutionNotFoundException;
import com.dasigconnect.backend.model.dto.institution.CreateInstitutionRequest;
import com.dasigconnect.backend.model.dto.institution.InstitutionDto;
import com.dasigconnect.backend.model.entity.Institution;
import com.dasigconnect.backend.model.entity.InstitutionStatus;
import com.dasigconnect.backend.repository.InstitutionRepository;

/**
 * Manages the institution lifecycle.
 *
 * State machine:
 *   INACTIVE  → (admin sends validator invitation)  → PENDING
 *   PENDING   → (validator activates account)       → ACTIVE
 *   ACTIVE    → (all validators deactivated)        → INACTIVE
 *   PENDING   → (last validator invitation cancelled, no active validators) → INACTIVE
 *
 * Invalid transitions are rejected with IllegalStateException → HTTP 409.
 */
@Service
@Transactional
public class InstitutionService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionService.class);

    private final InstitutionRepository institutionRepository;
    private final WorkspaceProvisionerService workspaceProvisioner;
    private final AuditLogService auditLogService;

    public InstitutionService(
            InstitutionRepository institutionRepository,
            WorkspaceProvisionerService workspaceProvisioner,
            AuditLogService auditLogService) {
        this.institutionRepository = institutionRepository;
        this.workspaceProvisioner = workspaceProvisioner;
        this.auditLogService = auditLogService;
    }

    /**
     * Creates a new institution with status INACTIVE and provisions its RLS workspace.
     */
    public InstitutionDto createInstitution(CreateInstitutionRequest request) {
        if (institutionRepository.existsByCode(request.getInstitutionCode())) {
            throw new IllegalArgumentException(
                    "Institution code '" + request.getInstitutionCode() + "' is already in use.");
        }

        String emailDomain = request.getEmailDomain().trim().toLowerCase();
        if (institutionRepository.existsByEmailDomain(emailDomain)) {
            throw new IllegalArgumentException(
                    "Email domain '" + emailDomain + "' is already in use.");
        }

        Institution institution = new Institution();
        institution.setName(request.getName());
        institution.setCode(request.getInstitutionCode());
        institution.setEmailDomain(emailDomain);
        institution.setStatus(InstitutionStatus.inactive);

        institution = institutionRepository.save(institution);

        workspaceProvisioner.provision(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_CREATED",
                institution.getId(),
                Map.of("name", institution.getName(), "code", institution.getCode())
        );

        log.info("Institution created: {} ({}), status=INACTIVE", institution.getName(), institution.getId());
        return InstitutionDto.from(institution);
    }

    @Transactional(readOnly = true)
    public java.util.List<InstitutionDto> listInstitutions() {
        return institutionRepository.findAll().stream()
                .map(InstitutionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InstitutionDto getInstitution(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));
        return InstitutionDto.from(institution);
    }

    /**
     * INACTIVE → PENDING. Called when an admin sends a validator invitation.
     */
    public void transitionToPending(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.inactive) {
            throw new IllegalStateException(
                    "Cannot transition institution " + institutionId + " to PENDING: "
                    + "current status is " + institution.getStatus()
                    + " (expected: inactive)");
        }

        institution.setStatus(InstitutionStatus.pending);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_PENDING",
                institutionId,
                Map.of("previousStatus", "inactive")
        );

        log.info("Institution {} transitioned INACTIVE → PENDING", institutionId);
    }

    /**
     * PENDING → ACTIVE. Called when the first validator activates their account.
     * Also accepts INACTIVE as a precondition to handle edge cases.
     */
    public void transitionToActive(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.pending
                && institution.getStatus() != InstitutionStatus.inactive) {
            throw new IllegalStateException(
                    "Cannot transition institution " + institutionId + " to ACTIVE: "
                    + "current status is " + institution.getStatus()
                    + " (expected: pending)");
        }

        String previousStatus = institution.getStatus().name();
        institution.setStatus(InstitutionStatus.active);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_ACTIVATED",
                institutionId,
                Map.of("previousStatus", previousStatus)
        );

        log.info("Institution {} transitioned {} → ACTIVE", institutionId, previousStatus.toUpperCase());
    }

    /**
     * ACTIVE or PENDING → INACTIVE. Called when all validators are deactivated/removed,
     * or when the last pending validator invitation is cancelled.
     */
    public void transitionToInactive(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.active
                && institution.getStatus() != InstitutionStatus.pending) {
            throw new IllegalStateException(
                    "Cannot transition institution " + institutionId + " to INACTIVE: "
                    + "current status is " + institution.getStatus()
                    + " (expected: active or pending)");
        }

        String previousStatus = institution.getStatus().name();
        institution.setStatus(InstitutionStatus.inactive);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_INACTIVE",
                institutionId,
                Map.of("previousStatus", previousStatus)
        );

        log.info("Institution {} transitioned {} → INACTIVE", institutionId, previousStatus.toUpperCase());
    }
}
