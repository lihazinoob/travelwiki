package com.example.travelwiki.common.exception;

import com.example.travelwiki.ai.exception.AiProviderException;
import com.example.travelwiki.ai.exception.AiResponseValidationException;
import com.example.travelwiki.auth.exception.InvalidGoogleTokenException;
import com.example.travelwiki.auth.exception.InvalidRefreshTokenException;
import com.example.travelwiki.auth.exception.RefreshTokenExpiredException;
import com.example.travelwiki.auth.exception.UserSuspendedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(this::validationMessage)
            .orElse("Request validation failed");

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                message,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.MALFORMED_REQUEST,
            "Request body is malformed or unreadable",
            request
        );
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidGoogleToken(
        InvalidGoogleTokenException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Google token verification failed: {}", exception.getMessage(), exception);

        return buildResponse(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.INVALID_GOOGLE_TOKEN,
            "Google ID token is invalid",
            request
        );
    }

    /**
     * Handles sign-in attempts from suspended or deleted accounts.
     *
     * <p>Returns {@code 403 Forbidden} so the client knows the credential is valid but
     * the account is blocked. The message from the exception is written to be safe for
     * clients to display. The user ID is already logged at WARN level inside the service
     * before this exception is thrown, so no additional logging is needed here.
     *
     * <p>Note: we intentionally do NOT log the exception object itself here because
     * {@link UserSuspendedException} carries the client-facing message, not internal
     * state. The relevant diagnostic information (userId, status) was already emitted
     * by the service.
     */
    @ExceptionHandler(UserSuspendedException.class)
    public ResponseEntity<ApiErrorResponse> handleUserSuspended(
        UserSuspendedException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.FORBIDDEN,
            ApiErrorCode.USER_SUSPENDED,
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(
        InvalidRefreshTokenException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Invalid refresh token presented: {}", exception.getMessage());
        return buildResponse(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.INVALID_REFRESH_TOKEN,
            "Refresh token is invalid or has been revoked",
            request
        );
    }

    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshTokenExpired(
        RefreshTokenExpiredException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.REFRESH_TOKEN_EXPIRED,
            "Refresh token has expired, please sign in again",
            request
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
        BadRequestException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(DestinationNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleDestinationNotSupported(
        DestinationNotSupportedException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.DESTINATION_NOT_SUPPORTED,
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
        NotFoundException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.NOT_FOUND,
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
        ForbiddenException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.FORBIDDEN,
            ApiErrorCode.FORBIDDEN,
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleAiProviderError(
        AiProviderException exception,
        HttpServletRequest request
    ) {
        LOGGER.error("AI provider call failed: {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            ApiErrorCode.AI_PROVIDER_ERROR,
            "AI service is temporarily unavailable",
            request
        );
    }

    @ExceptionHandler(AiResponseValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleAiResponseInvalid(
        AiResponseValidationException exception,
        HttpServletRequest request
    ) {
        LOGGER.error("AI response validation failed: {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiErrorCode.AI_RESPONSE_INVALID,
            "AI returned an invalid response",
            request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
    ) {
        LOGGER.error("Unexpected API error", exception);

        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiErrorCode.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
            request
        );
    }

    private String validationMessage(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        return defaultMessage == null ? "Request validation failed" : defaultMessage;
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
        HttpStatus status,
        ApiErrorCode code,
        String message,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(status)
            .body(ApiErrorResponse.of(code, message, request.getRequestURI()));
    }
}
