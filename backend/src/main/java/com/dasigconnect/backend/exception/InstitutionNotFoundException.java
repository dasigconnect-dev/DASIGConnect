package com.dasigconnect.backend.exception;

import java.util.UUID;

/**
 * Thrown when an institution lookup by ID or code returns no result.
 *
 * Per SRS Section 3.4.6.4, cross-institution access must return HTTP 404 (not
 * 403) to avoid revealing existence. This exception is therefore used for BOTH
 * "not found" and "access denied" cases on institution resources.
 */
public class InstitutionNotFoundException extends RuntimeException {

    public InstitutionNotFoundException(UUID id) {
        super("Institution not found: " + id);
    }

    public InstitutionNotFoundException(String code) {
        super("Institution not found: " + code);
    }
}
