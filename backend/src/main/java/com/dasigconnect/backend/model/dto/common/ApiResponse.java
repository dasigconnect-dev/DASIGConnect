package com.dasigconnect.backend.model.dto.common;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, ApiError error, String timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), Instant.now().toString());
    }
}
