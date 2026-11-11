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

    @Test
    void generatedSpecificationIsAvailableAndExported() throws Exception {
        String specification = mvc.perform(get("/v3/api-docs")
                .header("Authorization", TOKENS.customer().bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Path artifact = Path.of("target", "generated-openapi", "parcel-service.json");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, specification);
    }
}
