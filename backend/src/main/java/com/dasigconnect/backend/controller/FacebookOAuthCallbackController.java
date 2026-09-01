package com.dasigconnect.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dasigconnect.backend.service.TokenManagementService;

/**
 * Public landing point for the Facebook Page re-authentication flow started from
 * System Health → Integrations → "Re-Authenticate". Facebook redirects the
 * admin's browser here with {@code ?code=&state=}, so this endpoint must NOT be
 * behind {@code @PreAuthorize} — a top-level redirect carries no bearer token.
 * The {@code state} value (a one-time nonce minted by {@link TokenManagementService})
 * is what ties the callback back to the admin who initiated it.
 *
 * <p>The URL is registered as an OAuth redirect URI in the Facebook App and in
 * {@code app.facebook.oauth-redirect-uri}; keep the three in sync.
 */
@RestController
@RequestMapping("/api/v1/facebook")
public class FacebookOAuthCallbackController {

    private final TokenManagementService tokenManagementService;

    public FacebookOAuthCallbackController(TokenManagementService tokenManagementService) {
        this.tokenManagementService = tokenManagementService;
    }

    @GetMapping("/oauth-callback")
    public ResponseEntity<String> oauthCallback(
            @RequestParam String code,
            @RequestParam String state) {
        return ResponseEntity.ok(tokenManagementService.handleCallback(code, state));
    }
}
