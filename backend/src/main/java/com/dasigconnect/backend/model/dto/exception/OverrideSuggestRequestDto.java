package com.dasigconnect.backend.model.dto.exception;

import java.time.Instant;

public class OverrideSuggestRequestDto {

    private Instant suggestedSlot;
    private String message;

    public Instant getSuggestedSlot() { return suggestedSlot; }
    public void setSuggestedSlot(Instant suggestedSlot) { this.suggestedSlot = suggestedSlot; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
