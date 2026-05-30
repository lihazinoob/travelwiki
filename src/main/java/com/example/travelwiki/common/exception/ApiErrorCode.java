package com.example.travelwiki.common.exception;

public enum ApiErrorCode {
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INVALID_GOOGLE_TOKEN,
    USER_SUSPENDED,
    INVALID_REFRESH_TOKEN,
    REFRESH_TOKEN_EXPIRED,
    INTERNAL_SERVER_ERROR
}
