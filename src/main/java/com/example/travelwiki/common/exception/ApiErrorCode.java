package com.example.travelwiki.common.exception;

public enum ApiErrorCode {
    // Generic request errors
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    BAD_REQUEST,
    NOT_FOUND,
    FORBIDDEN,

    // Auth errors
    INVALID_GOOGLE_TOKEN,
    USER_SUSPENDED,
    INVALID_REFRESH_TOKEN,
    REFRESH_TOKEN_EXPIRED,

    // Business feature errors
    DESTINATION_NOT_SUPPORTED,
    TRIP_NOT_FOUND,

    // AI pipeline errors
    AI_PROVIDER_ERROR,
    AI_RESPONSE_INVALID,

    INTERNAL_SERVER_ERROR
}
