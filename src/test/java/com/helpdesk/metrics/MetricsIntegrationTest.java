package com.helpdesk.metrics;

import com.helpdesk.IntegrationTestBase;
import com.helpdesk.auth.domain.InviteCode;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.InviteCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricsIntegrationTest extends IntegrationTestBase {

    @Autowired InviteCodeRepository inviteRepo;

    private final Instant monday9 = Instant.parse("2026-01-05T12:00:00Z"); // seg 09:00 BRT

    @BeforeEach
    void ensureInvite() {
        if (inviteRepo.findById("AGENTE-2026").isEmpty()) {
            inviteRepo.save(new InviteCode("AGENTE-2026", Role.AGENTE,
                    Instant.parse("2030-12-31T23:59:59Z")));
        }
    }

    private String createTicket(String token, String priority, Instant at) throws Exception {
        when(time.now()).thenReturn(at);
        var result = mockMvc.perform(auth(post("/api/tickets"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Chamado %s","description":"descricao","priority":"%s"}
                                """.formatted(priority, priority))))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void transition(String token, String id, String status, Instant at) throws Exception {
        when(time.now()).thenReturn(at);
        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"status\":\"" + status + "\"}")))
                .andExpect(status().isOk());
    }

    private void comment(String token, String id, Instant at) throws Exception {
        when(time.now()).thenReturn(at);
        mockMvc.perform(auth(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/tickets/" + id + "/comments")
                                .param("body", "respondido"), token))
                .andExpect(status().isCreated());
    }

    private void seedTwoResolvedTickets(String client, String agent) throws Exception {
        // Ticket A: MEDIA, primeira resposta +1h, resolvido +3h -> dentro do SLA
        String a = createTicket(client, "MEDIA", monday9);
        transition(agent, a, "EM_ATENDIMENTO", monday9);
        comment(agent, a, monday9.plusSeconds(3600));
        transition(agent, a, "RESOLVIDO", monday9.plusSeconds(3 * 3600L));

        // Ticket B: CRITICA, resposta +2h, resolvido +10h (vence 17:00 = +8h) -> fora do SLA
        String b = createTicket(client, "CRITICA", monday9);
        transition(agent, b, "EM_ATENDIMENTO", monday9);
        comment(agent, b, monday9.plusSeconds(2 * 3600L));
        transition(agent, b, "RESOLVIDO", monday9.plusSeconds(10 * 3600L));
    }

    @Test
    void globalMetricsAreCorrect() throws Exception {
        String client = register("Ana", "mx1@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "max1@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        seedTwoResolvedTickets(client, agent);

        mockMvc.perform(auth(get("/api/metrics"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byStatus.RESOLVIDO").value(2))
                .andExpect(jsonPath("$.byStatus.ABERTO").value(0))
                .andExpect(jsonPath("$.slaCompliancePercent").value(50.0))
                .andExpect(jsonPath("$.avgFirstResponseHours").value(1.5))
                .andExpect(jsonPath("$.avgResolutionHours").value(6.5));
    }

    @Test
    void perAgentMetricsAreCorrect() throws Exception {
        String client = register("Ana", "mx2@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "max2@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        seedTwoResolvedTickets(client, agent);

        mockMvc.perform(auth(get("/api/metrics/agents"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].agentName").value("Agente"))
                .andExpect(jsonPath("$[0].assigned").value(2))
                .andExpect(jsonPath("$[0].resolved").value(2))
                .andExpect(jsonPath("$[0].slaCompliancePercent").value(50.0))
                .andExpect(jsonPath("$[0].avgFirstResponseHours").value(1.5));

        var agentId = mapper.readTree(
                        mockMvc.perform(auth(get("/api/metrics/agents"), client))
                                .andReturn().getResponse().getContentAsString())
                .get(0).get("agentId").asText();
        mockMvc.perform(auth(get("/api/metrics/agents/" + agentId), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(2));
    }

    @Test
    void unknownAgentIs404() throws Exception {
        String client = register("Ana", "mx3@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(get("/api/metrics/agents/999999"), client))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCannotBeCountedAsAgent() throws Exception {
        String client = register("Ana", "mx4@dev.io", "Senha@123", "CLIENTE", null);
        var clientId = mapper.readTree(
                        mockMvc.perform(auth(get("/api/users/me"), client))
                                .andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(auth(get("/api/metrics/agents/" + clientId), client))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyMetricsReturnNullsAndZeros() throws Exception {
        String client = register("Ana", "mx5@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(get("/api/metrics"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.byStatus.ABERTO").value(0))
                .andExpect(jsonPath("$.slaCompliancePercent").doesNotExist())
                .andExpect(jsonPath("$.avgResolutionHours").doesNotExist());
    }

    @Test
    void resolvedThenClosedStillCountsAsResolved() throws Exception {
        String client = register("Ana", "mx6@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "max6@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");

        String id = createTicket(client, "BAIXA", monday9);
        transition(agent, id, "EM_ATENDIMENTO", monday9);
        comment(agent, id, monday9.plusSeconds(3600));
        transition(agent, id, "RESOLVIDO", monday9.plusSeconds(2 * 3600L));
        transition(agent, id, "FECHADO", monday9.plusSeconds(3 * 3600L));

        mockMvc.perform(auth(get("/api/metrics"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.FECHADO").value(1))
                .andExpect(jsonPath("$.byStatus.RESOLVIDO").value(0))
                .andExpect(jsonPath("$.slaCompliancePercent").value(100.0))
                .andExpect(jsonPath("$.avgResolutionHours").value(2.0));

        mockMvc.perform(auth(get("/api/metrics/agents"), client))
                .andExpect(jsonPath("$[0].resolved").value(1))
                .andExpect(jsonPath("$[0].slaCompliancePercent").value(100.0));
    }

    @Test
    void metricsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
