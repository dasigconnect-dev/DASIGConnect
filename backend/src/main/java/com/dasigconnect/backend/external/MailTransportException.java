package com.dasigconnect.backend.external;

/**
 * Raised when an outbound email cannot be handed to the mail provider —
 * missing credentials, a non-2xx API response, or a transport-level failure.
 */
public class MailTransportException extends RuntimeException {

    public MailTransportException(String message) {
        super(message);
    }

    public MailTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
