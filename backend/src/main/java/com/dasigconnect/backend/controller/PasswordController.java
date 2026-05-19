package com.dasigconnect.backend.controller;

import com.dasigconnect.backend.model.dto.password.ForgotPasswordRequestDto;
import com.dasigconnect.backend.model.dto.password.ResetPasswordRequestDto;
import com.dasigconnect.backend.service.PasswordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordController {

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequestDto dto) {
        passwordService.requestReset(dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @RequestBody @Valid ResetPasswordRequestDto dto) {
        passwordService.resetPassword(dto);
        return ResponseEntity.noContent().build();
    }
}
