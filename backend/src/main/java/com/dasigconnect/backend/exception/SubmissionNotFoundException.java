package com.dasigconnect.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

public class SubmissionNotFoundException extends ResponseStatusException {
    public SubmissionNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Submission not found: " + id);
    }
}
