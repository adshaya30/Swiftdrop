package com.swiftdrop.parcel.create.controller;

import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inspect actual springdoc output, not annotation reflection. */
@TestPropertySource(properties = "SWAGGER_ENABLED=true")
class ParcelOpenApiTests extends AbstractParcelDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test
    void generatedSpecificationIsAvailableAndExported() throws Exception {
        String specification = mvc.perform(get("/v3/api-docs")
                .header("Authorization", TOKENS.customer().bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Path artifact = Path.of("target", "generated-openapi", "parcel-service.json");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, specification);
        var spec = json.readTree(specification);
        String base = "/api/v1/customer/parcels";
        for (String[] endpoint : new String[][]{{base, "post"}, {base, "get"},
                {base + "/{id}", "get"}, {base + "/{id}/cancel", "post"}}) {
            var operation = spec.path("paths").path(endpoint[0]).path(endpoint[1]);
            org.assertj.core.api.Assertions.assertThat(operation.isMissingNode()).isFalse();
            org.assertj.core.api.Assertions.assertThat(operation.path("security").get(0).has("customerBearer")).isTrue();
            for (String code : new String[]{"400", "401", "403"}) {
                org.assertj.core.api.Assertions.assertThat(operation.path("responses").has(code)).isTrue();
            }
            String success = endpoint[1].equals("post") && endpoint[0].equals(base) ? "201" : "200";
            org.assertj.core.api.Assertions.assertThat(operation.path("responses").path(success)
                    .path("content").toString()).contains("schema");
        }
        var created = spec.path("paths").path(base).path("post");
        org.assertj.core.api.Assertions.assertThat(created.path("responses").path("201").path("headers").has("Location")).isTrue();
        org.assertj.core.api.Assertions.assertThat(created.path("responses").path("201").path("headers").has("Idempotent-Replay")).isTrue();
        org.assertj.core.api.Assertions.assertThat(created.path("requestBody").path("content").toString()).contains("CustomerParcelCreationRequest");
        for (String code : new String[]{"409", "422", "503"}) {
            org.assertj.core.api.Assertions.assertThat(created.path("responses").has(code)).isTrue();
        }
        org.assertj.core.api.Assertions.assertThat(spec.path("paths").path(base + "/{id}").path("get")
                .path("responses").has("404")).isTrue();
        org.assertj.core.api.Assertions.assertThat(spec.path("components").path("securitySchemes")
                .path("customerBearer").path("scheme").asText()).isEqualTo("bearer");
        var cancel = spec.path("paths").path(base + "/{id}/cancel").path("post");
        for (String code : new String[]{"404", "409"}) {
            org.assertj.core.api.Assertions.assertThat(cancel.path("responses").has(code)).isTrue();
        }
        org.assertj.core.api.Assertions.assertThat(cancel.path("requestBody").path("content").toString()).contains("CustomerParcelCancellationRequest");
        org.assertj.core.api.Assertions.assertThat(spec.path("components").path("schemas")
                .path("CustomerParcelCancellationRequest").path("properties").path("reason").path("maxLength").asInt()).isEqualTo(300);
        org.assertj.core.api.Assertions.assertThat(spec.path("components").path("schemas")
                .path("CustomerParcelCancellationResponse").path("properties").fieldNames())
                .toIterable().containsExactlyInAnyOrder("id", "trackingNumber", "status", "statusChangedAt");
    }
}
