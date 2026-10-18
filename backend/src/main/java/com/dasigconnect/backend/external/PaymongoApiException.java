package com.dasigconnect.backend.external;

public class PaymongoApiException extends RuntimeException {
    public PaymongoApiException(String message) { super(message); }
    public PaymongoApiException(String message, Throwable cause) { super(message, cause); }
}
