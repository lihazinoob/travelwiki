package com.example.travelwiki.common.exception;

public enum ApiErrorCode {
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INVALID_GOOGLE_TOKEN,
    /**
     * The user's account is in a non-active state (SUSPENDED or DELETED).
     * Maps to HTTP 403 Forbidden.
     */
    USER_SUSPENDED,
    INTERNAL_SERVER_ERROR
}
