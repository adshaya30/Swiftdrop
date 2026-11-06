package com.swiftdrop.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.ProblemBody;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.MDC;

/** Writes RFC 9457 bodies from servlet filters and security handlers (application/problem+json). */
public class ProblemWriter {

    private final ObjectMapper mapper;

    public ProblemWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        response.setStatus(code.status());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ProblemBody.of(code, detail, traceId()));
    }

    /** The request id of the current request, or a fresh id when none is set. */
    public static String traceId() {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }
}
