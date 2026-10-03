package com.helpdesk.auth;

import com.helpdesk.IntegrationTestBase;
import com.helpdesk.auth.domain.InviteCode;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.InviteCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired InviteCodeRepository inviteRepo;

    @BeforeEach
    void ensureInvite() {
        if (inviteRepo.findById("AGENTE-2026").isEmpty()) {
            inviteRepo.save(new InviteCode("AGENTE-2026", Role.AGENTE,
                    Instant.parse("2030-12-31T23:59:59Z")));
        }
    }

    @Test
    void registerClientReturnsTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"Ana","email":"ana1@dev.io","password":"Senha@123","role":"CLIENTE"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void registerAgentWithoutInviteIsConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"Bia","email":"bia1@dev.io","password":"Senha@123","role":"AGENTE"}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void registerAgentWithInviteConsumesCode() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"Bia","email":"bia2@dev.io","password":"Senha@123",
                                 "role":"AGENTE","inviteCode":"AGENTE-2026"}
                                """)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"Caio","email":"caio@dev.io","password":"Senha@123",
                                 "role":"AGENTE","inviteCode":"AGENTE-2026"}
                                """)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerWithInvalidBodyIs422() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"","email":"not-an-email","password":"1","role":"CLIENTE"}
                                """)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations.name").exists())
                .andExpect(jsonPath("$.violations.email").exists());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        register("Ana", "dup@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"Outra","email":"dup@dev.io","password":"Senha@123","role":"CLIENTE"}
                                """)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIs401ProblemDetail() throws Exception {
        register("Ana", "ana2@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"ana2@dev.io","password":"Errada@1"}
                                """)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refreshRotatesAndReuseIsRejected() throws Exception {
        register("Ana", "ana3@dev.io", "Senha@123", "CLIENTE", null);
        var first = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"ana3@dev.io","password":"Senha@123"}
                                """)))
                .andExpect(status().isOk()).andReturn();
        String refresh1 = mapper.readTree(first.getResponse().getContentAsString())
                .get("refreshToken").asText();

        String body2 = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"refreshToken\":\"" + refresh1 + "\"}")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refresh2 = mapper.readTree(body2).get("refreshToken").asText();
        org.assertj.core.api.Assertions.assertThat(refresh2).isNotEqualTo(refresh1);

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"refreshToken\":\"" + refresh1 + "\"}")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"refreshToken\":\"" + refresh2 + "\"}")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        register("Ana", "ana4@dev.io", "Senha@123", "CLIENTE", null);
        var login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"ana4@dev.io","password":"Senha@123"}
                                """)))
                .andExpect(status().isOk()).andReturn();
        String refresh = mapper.readTree(login.getResponse().getContentAsString())
                .get("refreshToken").asText();
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"refreshToken\":\"" + refresh + "\"}")))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"refreshToken\":\"" + refresh + "\"}")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void badJsonBodyIs400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
