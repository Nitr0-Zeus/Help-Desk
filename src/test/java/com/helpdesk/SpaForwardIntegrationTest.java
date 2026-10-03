package com.helpdesk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaForwardIntegrationTest extends IntegrationTestBase {

    @Test
    void spaClientRoutesForwardToIndexHtml() throws Exception {
        // MockMvc records the forward without executing it; the real HTML
        // serving is covered by SpaForwardFilterTest + the manual smoke run
        var result = mockMvc.perform(get("/tickets/5"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getForwardedUrl()).isEqualTo("/index.html");
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/metrics")).andExpect(status().isOk());
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void apiStaysProtectedAndJson() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/nonexistent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void docsAndHealthStayPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unknownAssetStill404s() throws Exception {
        mockMvc.perform(get("/does-not-exist.txt")).andExpect(status().isNotFound());
    }
}
