package com.swiftdrop.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.testapp.TestApp;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = TestApp.class, properties = "INTERNAL_API_KEY=" + TestApp.INTERNAL_KEY)
@AutoConfigureMockMvc
@Tag("TST-013")
class ErrorHandlingTest {

    @Autowired
    MockMvc mvc;

    private String auth() {
        return TestApp.TOKENS.customer().userId(UUID.randomUUID()).bearer();
    }

    private ResultActions call(String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, auth()));
    }

    @Test
    @DisplayName("TST-013 DomainException: its status and code, extensions at top level (FEE_CHANGED newFee)")
    void domainExceptionWithExtensions() throws Exception {
        call("/api/v1/fee")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FEE_CHANGED"))
                .andExpect(jsonPath("$.newFee").value("650.00"))
                .andExpect(jsonPath("$.type").value("https://swiftdrop.example/errors/fee-changed"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.extensions").doesNotExist());
    }

    @Test
    @DisplayName("TST-013 unexpected error: 500 INTERNAL_ERROR with traceId, no stack trace, class name or SQL")
    void internalError() throws Exception {
        call("/api/v1/boom")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(content().string(Matchers.not(Matchers.containsString("com.swiftdrop"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("SQL"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("stackTrace"))));
    }

    @Test
    @DisplayName("TST-013 validation errors: 400 VALIDATION_ERROR with errors[field, message]")
    void validation() throws Exception {
        mvc.perform(post("/api/v1/validate").header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    @DisplayName("TST-013 unknown JSON property and unreadable body: 400 VALIDATION_ERROR")
    void unknownProperty() throws Exception {
        mvc.perform(post("/api/v1/validate").header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ok\",\"extra\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("extra"));
        mvc.perform(post("/api/v1/validate").header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TST-013 unsupported media type: 415 UNSUPPORTED_MEDIA_TYPE")
    void mediaType() throws Exception {
        mvc.perform(post("/api/v1/validate").header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("TST-013 optimistic lock: 409 CONCURRENT_UPDATE")
    void optimisticLock() throws Exception {
        call("/api/v1/optimistic").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }

    @Test
    @DisplayName("TST-013 upload too large: 413 PAYLOAD_TOO_LARGE")
    void tooLarge() throws Exception {
        call("/api/v1/toolarge").andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    @DisplayName("TST-013 no handler and wrong method: 404 NOT_FOUND")
    void notFound() throws Exception {
        call("/api/v1/does-not-exist").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/v1/secured").header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
