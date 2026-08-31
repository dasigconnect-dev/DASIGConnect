package com.dasigconnect.backend.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class PasswordPolicy {

    static final int MIN_LENGTH = 12;

    private static final List<String> COMMON_FRAGMENTS = List.of(
            "password",
            "qwerty",
            "admin",
            "dasig",
            "welcome",
            "letmein",
            "123",
            "abc");

    private PasswordPolicy() {
    }

    static void validate(String password, String email, String firstName, String lastName) {
        if (password == null || password.isBlank()) {
            reject("Password is required.");
        }
        if (password.length() < MIN_LENGTH) {
            reject("Password must be at least 12 characters.");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            reject("Password cannot contain spaces.");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            reject("Password must include an uppercase letter.");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            reject("Password must include a lowercase letter.");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            reject("Password must include a number.");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            reject("Password must include a special character.");
        }

        String normalized = password.toLowerCase(Locale.ROOT);
        for (String fragment : COMMON_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                reject("Password is too easy to guess.");
            }
        }
        rejectIfContainsIdentity(normalized, email);
        rejectIfContainsIdentity(normalized, firstName);
        rejectIfContainsIdentity(normalized, lastName);
    }

    private static void rejectIfContainsIdentity(String normalizedPassword, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at > 0) {
            normalized = normalized.substring(0, at);
        }
        normalized = normalized.replaceAll("[^\\p{Alnum}]", "");
        if (normalized.length() >= 3 && normalizedPassword.contains(normalized)) {
            reject("Password cannot contain your name or email.");
        }
    }

    private static void reject(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
