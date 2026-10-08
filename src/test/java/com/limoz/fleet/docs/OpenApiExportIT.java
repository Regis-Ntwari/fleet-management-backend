package com.limoz.fleet.docs;

import com.limoz.fleet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the OpenAPI contract is served and keeps docs/openapi.json in sync with the code. */
class OpenApiExportIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("OpenAPI document is published and exported to docs/openapi.json")
    void exportOpenApi() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("LIMOZ Rwanda - Fleet Operations API"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/vehicles']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dashboard/summary']").exists())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var root = json.readTree(body);
        assertThat(root.get("paths").size()).isGreaterThan(150);
        Path docs = Path.of("docs");
        if (Files.isDirectory(docs)) {
            Files.writeString(docs.resolve("openapi.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
        }
    }
}
