package com.swiftdrop.common.error;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** RFC 9457 Problem Details body shared by common-lib and the gateway. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemBody(
        String type,
        String title,
        int status,
        String detail,
        String code,
        String traceId,
        Instant timestamp,
        List<FieldError> errors,
        @JsonIgnore Map<String, Object> extensions) {

    public static final String TYPE_PREFIX = "https://swiftdrop.example/errors/";

    public static ProblemBody of(ErrorCode code, String detail, String traceId,
                                 List<FieldError> errors, Map<String, Object> extensions) {
        return new ProblemBody(
                TYPE_PREFIX + code.name().toLowerCase().replace('_', '-'),
                code.title(),
                code.status(),
                detail,
                code.name(),
                traceId,
                Instant.now(),
                errors == null || errors.isEmpty() ? null : List.copyOf(errors),
                extensions == null ? Map.of() : Map.copyOf(extensions));
    }

    public static ProblemBody of(ErrorCode code, String detail, String traceId) {
        return of(code, detail, traceId, null, null);
    }

    /** Extension members are written at the top level of the JSON object. */
    @JsonAnyGetter
    public Map<String, Object> anyExtensions() {
        return extensions;
    }
}
