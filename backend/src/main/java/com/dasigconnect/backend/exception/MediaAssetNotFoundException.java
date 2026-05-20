package com.dasigconnect.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

public class MediaAssetNotFoundException extends ResponseStatusException {
    public MediaAssetNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Media asset not found: " + id);
    }
}
