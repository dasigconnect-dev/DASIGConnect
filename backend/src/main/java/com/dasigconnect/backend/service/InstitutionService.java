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
 * Manages the institution lifecycle for UC-1.2.
 *
 * Institution state machine: ONBOARDING → ACTIVE (when first Validator
 * activates their account — event-driven) ACTIVE → INACTIVE_NO_VALIDATOR (when
 * all Validators become inactive — event-driven by M2) INACTIVE_NO_VALIDATOR →
 * ACTIVE (when a new Validator activates)
 *
 * Invalid transitions are rejected with IllegalStateException → HTTP 409.
 *
 * This service does NOT directly manage Users or send emails — those are M2's
 * domain. M2's InvitationService calls transitionToActive() when the first
 * Validator activates.
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
     * Creates a new institution and provisions its isolated workspace.
     *
     * UC-1.2 flow step 1: Administrator submits institution name + code →
     * Institution created with status = onboarding →
     * WorkspaceProvisionerService sets up RLS context → AuditLog records the
     * action
     *
     * @param request the validated CreateInstitutionRequest from the controller
     * @return InstitutionDto of the newly created institution
     * @throws IllegalArgumentException if the institution code is already taken
     */
    public InstitutionDto createInstitution(CreateInstitutionRequest request) {
        // Validate uniqueness of code before persisting
        if (institutionRepository.existsByCode(request.getInstitutionCode())) {
            throw new IllegalArgumentException(
                    "Institution code '" + request.getInstitutionCode() + "' is already in use.");
        }

        Institution institution = new Institution();
        institution.setName(request.getName());
        institution.setCode(request.getInstitutionCode()); // M1: field is 'code', not 'institutionCode'
        institution.setStatus(InstitutionStatus.onboarding);

        institution = institutionRepository.save(institution);

        // Provision RLS workspace context for this institution
        workspaceProvisioner.provision(institution);

        // Audit log — null actor = system-initiated (Administrator identified by SecurityContext in controller)
        auditLogService.recordSystemAction(
                "INSTITUTION_CREATED",
                institution.getId(),
                Map.of("name", institution.getName(), "code", institution.getCode())
        );

        log.info("Institution created: {} ({}), status=ONBOARDING", institution.getName(), institution.getId());
        return InstitutionDto.from(institution);
    }

    /**
     * Retrieves an institution by ID. Returns HTTP 404 (not 403) for
     * missing/inaccessible institutions per SRS 3.4.6.4.
     *
     * @param institutionId the institution UUID
     * @return InstitutionDto
     * @throws InstitutionNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public InstitutionDto getInstitution(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));
        return InstitutionDto.from(institution);
    }

    /**
     * Transitions an institution from ONBOARDING → ACTIVE.
     *
     * Called by M2's InvitationService (event-driven) when the first Validator
     * activates their account. No Administrator action is required.
     *
     * @param institutionId the institution to activate
     * @throws InstitutionNotFoundException if institution does not exist
     * @throws IllegalStateException if institution is not in ONBOARDING status
     */
    public void transitionToActive(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.onboarding) {
            throw new IllegalStateException(
                    "Cannot transition institution " + institutionId + " to ACTIVE: "
                    + "current status is " + institution.getStatus()
                    + " (expected: onboarding)");
        }

        institution.setStatus(InstitutionStatus.active);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_ACTIVATED",
                institutionId,
                Map.of("previousStatus", "onboarding")
        );

        log.info("Institution {} transitioned ONBOARDING → ACTIVE", institutionId);
    }

    /**
     * Transitions an institution from ACTIVE → INACTIVE_NO_VALIDATOR.
     *
     * Called by M2's UserService (event-driven) when the last active Validator
     * of an institution becomes inactive or is removed.
     *
     * Per SRS: all pending submissions are escalated to Administrator (that
     * escalation logic is in M5's SubmissionService).
     *
     * @param institutionId the institution to mark inactive
     */
    public void transitionToInactiveNoValidator(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.active) {
            throw new IllegalStateException(
                    "Cannot transition institution " + institutionId + " to INACTIVE_NO_VALIDATOR: "
                    + "current status is " + institution.getStatus()
                    + " (expected: active)");
        }

        institution.setStatus(InstitutionStatus.inactive_no_validator);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_INACTIVE_NO_VALIDATOR",
                institutionId,
                Map.of("previousStatus", "active")
        );

        log.info("Institution {} transitioned ACTIVE → INACTIVE_NO_VALIDATOR", institutionId);
    }

    /**
     * Transitions an institution from INACTIVE_NO_VALIDATOR → ACTIVE.
     *
     * Called when a new Validator activates for an institution that lost all
     * its validators.
     *
     * @param institutionId the institution to reactivate
     */
    public void reactivate(UUID institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        if (institution.getStatus() != InstitutionStatus.inactive_no_validator) {
            throw new IllegalStateException(
                    "Cannot reactivate institution " + institutionId + ": "
                    + "current status is " + institution.getStatus()
                    + " (expected: inactive_no_validator)");
        }

        institution.setStatus(InstitutionStatus.active);
        institutionRepository.save(institution);

        auditLogService.recordSystemAction(
                "INSTITUTION_REACTIVATED",
                institutionId,
                Map.of("previousStatus", "inactive_no_validator")
        );

        log.info("Institution {} reactivated INACTIVE_NO_VALIDATOR → ACTIVE", institutionId);
    }
}
