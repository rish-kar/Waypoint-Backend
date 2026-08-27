package com.waypoint.backend.controller.advice;

import com.waypoint.backend.model.common.ApiErrorResponse;
import com.waypoint.backend.utilities.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.status().is5xxServerError()) {
            logServerError(exception, request, exception.status());
        } else {
            logClientError(exception, request, exception.status(), exception.code(), exception.getMessage());
        }
        return error(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        String detail = message.isBlank() ? "Invalid request" : message;
        logClientError(exception, request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail);
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail, request);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        Throwable rootCause = rootCause(exception);
        String detail = rootCause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.getMessage();
        }
        if (detail == null || detail.isBlank()) {
            detail = "Invalid request body";
        }
        logClientError(exception, request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail);
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        logClientError(exception, request, HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnhandled(Exception exception, HttpServletRequest request) {
        logServerError(exception, request, HttpStatus.INTERNAL_SERVER_ERROR);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request);
    }

    private void logClientError(
            Exception exception,
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String detail
    ) {
        Throwable rootCause = rootCause(exception);
        LOGGER.atWarn()
                .setCause(exception)
                .addKeyValue("event", "client_request_rejected")
                .addKeyValue("status", status.value())
                .addKeyValue("code", code)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("exception_type", exception.getClass().getSimpleName())
                .addKeyValue("root_cause_type", rootCause.getClass().getSimpleName())
                .addKeyValue("detail", singleLine(detail))
                .log("Client request rejected");
    }

    private void logServerError(Exception exception, HttpServletRequest request, HttpStatus status) {
        Throwable rootCause = rootCause(exception);
        LOGGER.atError()
                .setCause(exception)
                .addKeyValue("event", "server_request_failed")
                .addKeyValue("status", status.value())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("exception_type", exception.getClass().getSimpleName())
                .addKeyValue("root_cause_type", rootCause.getClass().getSimpleName())
                .addKeyValue("detail", singleLine(rootCause.getMessage()))
                .log("Server request failed");
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String singleLine(String value) {
        if (value == null || value.isBlank()) {
            return "unavailable";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 2000 ? normalized : normalized.substring(0, 2000);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI()
        ));
    }
}
