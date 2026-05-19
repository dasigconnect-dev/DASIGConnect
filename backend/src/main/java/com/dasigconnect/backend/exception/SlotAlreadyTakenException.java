package com.dasigconnect.backend.exception;

/**
 * Thrown by SlotReservationService when two Contributors attempt to reserve the
 * same slot simultaneously and the DB unique constraint fires.
 *
 * Returned as HTTP 409 Conflict so the frontend (M5) can prompt the Contributor
 * to select a different slot.
 */
public class SlotAlreadyTakenException extends RuntimeException {

    public SlotAlreadyTakenException(String message) {
        super(message);
    }
}
