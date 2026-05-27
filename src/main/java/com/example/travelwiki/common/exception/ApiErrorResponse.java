package com.example.travelwiki.common.exception;

import java.time.Instant;

public record ApiErrorResponse(
    boolean success,
    String code,
    String message,
    String path,
    Instant timestamp
) {
    public static ApiErrorResponse of(ApiErrorCode code, String message, String path) {
        return new ApiErrorResponse(false, code.name(), message, path, Instant.now());
    }
}
