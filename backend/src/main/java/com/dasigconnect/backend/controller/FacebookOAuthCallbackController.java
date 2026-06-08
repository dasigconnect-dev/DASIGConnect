package com.dasigconnect.backend.controller;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.dasigconnect.backend.service.TokenManagementService;

/**
 * Public Meta OAuth callback.
 *
 * The browser returns here without the DASIGConnect JWT header, so the one-time
 * OAuth state token is the security boundary for this endpoint.
 */
@RestController
public class FacebookOAuthCallbackController {

    private final TokenManagementService tokenManagementService;
    private final String frontendBaseUrl;

    public FacebookOAuthCallbackController(
            TokenManagementService tokenManagementService,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.tokenManagementService = tokenManagementService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @GetMapping("/api/admin/resolution/tokens/oauth-callback")
    public ResponseEntity<Void> oauthCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (error != null && !error.isBlank()) {
            String message = errorDescription == null || errorDescription.isBlank()
                    ? "Facebook re-authentication was cancelled or denied: " + error
                    : errorDescription;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Facebook OAuth callback is missing code or state.");
        }

        tokenManagementService.handleCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(successRedirect())
                .build();
    }

    private URI successRedirect() {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .replacePath("/admin/resolution")
                .queryParam("tab", "system-audit")
                .queryParam("facebook", "connected")
                .build()
                .toUri();
    }
}
