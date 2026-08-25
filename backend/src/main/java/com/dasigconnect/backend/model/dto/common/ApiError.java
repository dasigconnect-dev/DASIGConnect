package com.dasigconnect.backend.model.dto.common;

public record ApiError(String code, String message, Object details) {
}
