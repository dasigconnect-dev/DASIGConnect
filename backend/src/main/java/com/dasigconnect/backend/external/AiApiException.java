package com.dasigconnect.backend.external;

/**
 * Common base exception for all AI provider integrations (Gemini, Claude, etc.).
 */
public class AiApiException extends RuntimeException {

    public AiApiException(String message) {
        super(message);
    }

    public AiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

