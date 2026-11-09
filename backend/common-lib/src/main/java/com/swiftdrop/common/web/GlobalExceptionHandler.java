package com.swiftdrop.common.web;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.error.ProblemBody;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception to an RFC 9457 body with an SRS 7.2 code. The detail is always generic and safe: no stack
 * trace, class name, SQL, path or personal data (BR-28). Unexpected errors are logged with the request id and
 * answered as 500 INTERNAL_ERROR with the traceId only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemBody> domain(DomainException ex) {
        return respond(ProblemBody.of(ex.code(), ex.detail(), ProblemWriter.traceId(), ex.errors(), ex.extensions()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemBody> bind(BindException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return validation("Request validation failed", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemBody> constraint(ConstraintViolationException ex) {
        List<FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validation("Request validation failed", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemBody> methodValidation(HandlerMethodValidationException ex) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldError(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return validation("Request validation failed", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemBody> unreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof UnrecognizedPropertyException u) {
            return validation("Request validation failed", List.of(new FieldError(u.getPropertyName(), "Unknown property")));
        }
        return validation("Malformed or unreadable request body", List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemBody> badParameter(Exception ex) {
        return validation("Invalid request parameter", List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ProblemBody> notFound(Exception ex) {
        // SRS 7.2 has no 405 code: an unsupported method on a path is answered like an unknown resource.
        return respond(ProblemBody.of(ErrorCode.NOT_FOUND, "Resource not found", ProblemWriter.traceId()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemBody> tooLarge(MaxUploadSizeExceededException ex) {
        return respond(ProblemBody.of(ErrorCode.PAYLOAD_TOO_LARGE, "Payload too large", ProblemWriter.traceId()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemBody> mediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(ProblemBody.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", ProblemWriter.traceId()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemBody> optimisticLock(ObjectOptimisticLockingFailureException ex) {
        return respond(ProblemBody.of(ErrorCode.CONCURRENT_UPDATE,
                "The resource was modified by another request, retry", ProblemWriter.traceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemBody> denied(AccessDeniedException ex) {
        return respond(ProblemBody.of(ErrorCode.ACCESS_DENIED, "Access denied", ProblemWriter.traceId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemBody> unauthenticated(AuthenticationException ex) {
        return respond(ProblemBody.of(ErrorCode.UNAUTHENTICATED, "Authentication required", ProblemWriter.traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemBody> unexpected(Exception ex) {
        String traceId = ProblemWriter.traceId();
        log.error("Unexpected error, traceId={}", traceId, ex);
        return respond(ProblemBody.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", traceId));
    }

    private ResponseEntity<ProblemBody> validation(String detail, List<FieldError> errors) {
        return respond(ProblemBody.of(ErrorCode.VALIDATION_ERROR, detail, ProblemWriter.traceId(), errors, null));
    }

    private ResponseEntity<ProblemBody> respond(ProblemBody body) {
        return ResponseEntity.status(body.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
