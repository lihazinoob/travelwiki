package com.example.travelwiki.auth.exception;

/**
 * Thrown when a sign-in is attempted for a user whose account is in a non-active state,
 * specifically {@code SUSPENDED} or {@code DELETED}.
 *
 * <p>This is a domain exception that crosses the service-to-controller boundary.
 * The {@link com.example.travelwiki.common.exception.GlobalExceptionHandler} maps it
 * to {@code HTTP 403 Forbidden}.
 *
 * <p>The message is written to be returned to the client directly. Internal details
 * (e.g. the actual status, the user ID) must be logged at the service layer before
 * throwing this exception, and must never be included in the message itself to avoid
 * leaking account state to potential attackers.
 */
public class UserSuspendedException extends RuntimeException {

    public UserSuspendedException(String message) {
        super(message);
    }
}
