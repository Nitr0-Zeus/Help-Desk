package com.helpdesk;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiIntegrationTest extends IntegrationTestBase {

    @Test
    void apiDocsExposeMainEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/auth/login.post").exists())
                .andExpect(jsonPath("$.paths./api/auth/register.post").exists())
                .andExpect(jsonPath("$.paths./api/tickets.get").exists())
                .andExpect(jsonPath("$.paths./api/tickets/{id}.get").exists())
                .andExpect(jsonPath("$.paths./api/tickets/{id}/status.post").exists())
                .andExpect(jsonPath("$.paths./api/tickets/{id}/comments.post").exists())
                .andExpect(jsonPath("$.paths./api/tickets/{id}/timeline.get").exists())
                .andExpect(jsonPath("$.paths./api/metrics.get").exists())
                .andExpect(jsonPath("$.paths./api/metrics/agents.get").exists())
                .andExpect(jsonPath("$.paths./api/attachments/{id}.get").exists())
                .andExpect(jsonPath("$.paths./api/sla-policies.get").exists())
                .andExpect(jsonPath("$.paths./api/users/me.get").exists());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void openApiDeclaresBearerSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                .andExpect(jsonPath("$.info.title").value("Help Desk API"));
    }
}
