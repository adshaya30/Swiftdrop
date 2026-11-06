package com.swiftdrop.common.error;

import java.util.List;
import java.util.Map;

/** Business error carrying an SRS 7.2 code. The detail must be safe (no personal data, no internals). */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldError> errors;
    private final Map<String, Object> extensions;

    public DomainException(ErrorCode code, String detail) {
        this(code, detail, List.of(), Map.of());
    }

    public DomainException(ErrorCode code, String detail, List<FieldError> errors) {
        this(code, detail, errors, Map.of());
    }

    public DomainException(ErrorCode code, String detail, List<FieldError> errors, Map<String, Object> extensions) {
        super(detail);
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }

    public ErrorCode code() {
        return code;
    }

    public String detail() {
        return getMessage();
    }

    public List<FieldError> errors() {
        return errors;
    }

    /** Extra response members, for example the new fee of FEE_CHANGED. */
    public Map<String, Object> extensions() {
        return extensions;
    }
}
