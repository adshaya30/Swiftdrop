package com.swiftdrop.auth.exception;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Converts domain exceptions to RFC 9457 Problem Details responses.
 * SRS v2.1 – Section 7.2
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -----------------------------------------------------------------------
    // HTTP 409 – Duplicate email
    // -----------------------------------------------------------------------

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<Map<String, Object>> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException ex, HttpServletRequest request) {

        log.warn("Duplicate registration attempt for path {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problem(
                        HttpStatus.CONFLICT,
                        "EMAIL_ALREADY_REGISTERED",
                        "An account with this email address already exists.",
                        request.getRequestURI(),
                        null));
    }

    // -----------------------------------------------------------------------
    // HTTP 400 – Invalid phone
    // -----------------------------------------------------------------------

    @ExceptionHandler(InvalidPhoneException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPhone(
            InvalidPhoneException ex, HttpServletRequest request) {

        List<Map<String, String>> fieldErrors = List.of(
                Map.of("field", "phone", "message", "Must be a valid Sri Lankan phone number (e.g. 0771234567 or +94771234567)"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(problem(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Request validation failed.",
                        request.getRequestURI(),
                        fieldErrors));
    }

    // -----------------------------------------------------------------------
    // HTTP 400 – Password policy violation
    // -----------------------------------------------------------------------

    @ExceptionHandler(PasswordPolicyException.class)
    public ResponseEntity<Map<String, Object>> handlePasswordPolicy(
            PasswordPolicyException ex, HttpServletRequest request) {

        List<Map<String, String>> fieldErrors = List.of(
                Map.of("field", "password", "message", ex.getMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(problem(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Request validation failed.",
                        request.getRequestURI(),
                        fieldErrors));
    }

    // -----------------------------------------------------------------------
    // HTTP 400 – Bean Validation (@Valid)
    // -----------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<Map<String, String>> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("field", error instanceof FieldError fe ? fe.getField() : error.getObjectName());
                    m.put("message", error.getDefaultMessage());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(problem(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Request validation failed.",
                        request.getRequestURI(),
                        fieldErrors));
    }

    // -----------------------------------------------------------------------
    // HTTP 400 – Unknown JSON property (e.g., "role" in request body)
    // -----------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String detail = "Malformed or unreadable JSON request body.";
        List<Map<String, String>> fieldErrors = null;

        if (ex.getCause() instanceof UnrecognizedPropertyException upe) {
            String unknownField = upe.getPropertyName();
            log.warn("Unknown property '{}' in request to {}", unknownField, request.getRequestURI());
            detail = "Unknown field(s) in request body.";
            fieldErrors = List.of(Map.of(
                    "field", unknownField,
                    "message", "Unknown property '" + unknownField + "' is not allowed."));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(problem(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        detail,
                        request.getRequestURI(),
                        fieldErrors));
    }

    // -----------------------------------------------------------------------
    // HTTP 500 – Fallback
    // -----------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR",
                        "An unexpected error occurred.",
                        request.getRequestURI(),
                        null));
    }

    // -----------------------------------------------------------------------
    // RFC 9457 Problem Details builder
    // -----------------------------------------------------------------------

    private Map<String, Object> problem(HttpStatus status, String code, String detail,
                                        String instance, List<Map<String, String>> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://swiftdrop.com/errors/" + code.toLowerCase().replace('_', '-'));
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", instance);
        body.put("code", code);
        body.put("traceId", UUID.randomUUID().toString());
        body.put("timestamp", Instant.now().toString());
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }
        return body;
    }
}
