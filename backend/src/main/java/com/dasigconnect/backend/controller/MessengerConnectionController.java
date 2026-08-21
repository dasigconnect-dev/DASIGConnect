package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.notification.MessengerConnectionDto;
import com.dasigconnect.backend.model.dto.notification.MessengerLinkCodeDto;
import com.dasigconnect.backend.security.JwtUserDetails;
import com.dasigconnect.backend.service.MessengerConnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/messenger/connection")
public class MessengerConnectionController {
    private final MessengerConnectionService service;

    public MessengerConnectionController(MessengerConnectionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessengerConnectionDto> status(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(service.status(user));
    }

    @PostMapping("/link-code")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessengerLinkCodeDto> createCode(
            @AuthenticationPrincipal JwtUserDetails user) {
        return ResponseEntity.ok(service.createLinkCode(user));
    }
}
