package com.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.common.time.TimeProvider;
import com.helpdesk.security.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static {
        deleteTestDatabase();
    }

    private static void deleteTestDatabase() {
        Path db = Path.of("target", "helpdesk-test.db");
        try {
            Files.deleteIfExists(db);
            Files.deleteIfExists(Path.of(db + "-wal"));
            Files.deleteIfExists(Path.of(db + "-shm"));
            Files.createDirectories(Path.of("target"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reset test database", e);
        }
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper mapper;
    @MockBean protected TimeProvider time;

    @BeforeEach
    void defaultClock() {
        when(time.now()).thenAnswer(i -> Instant.now());
    }

    protected String register(String name, String email, String password, String role, String inviteCode) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);
        body.put("role", role);
        if (inviteCode != null) body.put("inviteCode", inviteCode);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return token(result);
    }

    protected String login(String email, String password) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("email", email);
        body.put("password", password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return token(result);
    }

    protected String token(MvcResult result) throws Exception {
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    protected String json(Object value) {
        if (value instanceof String raw) {
            return raw;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
}
