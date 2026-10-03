package com.helpdesk.ticket;

import com.helpdesk.IntegrationTestBase;
import com.helpdesk.auth.domain.InviteCode;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.InviteCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketIntegrationTest extends IntegrationTestBase {

    @Autowired InviteCodeRepository inviteRepo;

    @BeforeEach
    void ensureInvite() {
        if (inviteRepo.findById("AGENTE-2026").isEmpty()) {
            inviteRepo.save(new InviteCode("AGENTE-2026", Role.AGENTE,
                    Instant.parse("2030-12-31T23:59:59Z")));
        }
    }

    private String newTicket(String token, String priority) throws Exception {
        var result = mockMvc.perform(auth(post("/api/tickets"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Impressora nao imprime","description":"Fila zerada no servidor",
                                 "priority":"%s"}
                                """.formatted(priority))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocol").isNotEmpty())
                .andExpect(jsonPath("$.dueAt").isNotEmpty())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void clientCreatesOpenTicketWithSlaDeadlines() throws Exception {
        String client = register("Ana", "tk1@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(post("/api/tickets"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Monitor piscando","description":"Liga e desliga sozinho",
                                 "priority":"MEDIA"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.firstResponseDueAt").isNotEmpty())
                .andExpect(jsonPath("$.dueAt").isNotEmpty())
                .andExpect(jsonPath("$.slaBreached").value(false));
    }

    @Test
    void agentCreatesTicketAlreadyAssigned() throws Exception {
        String agent = register("Agente", "tag-tk@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        mockMvc.perform(auth(post("/api/tickets"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Servidor fora do ar","description":"Ping falha",
                                 "priority":"CRITICA"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.assignedToId").isNumber());
    }

    @Test
    void clientSeesOnlyOwnTickets() throws Exception {
        String client1 = register("Ana", "tk2@dev.io", "Senha@123", "CLIENTE", null);
        String client2 = register("Bia", "tk3@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client1, "MEDIA");

        mockMvc.perform(auth(get("/api/tickets"), client1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(auth(get("/api/tickets"), client2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(auth(get("/api/tickets/" + id), client1))
                .andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/tickets/" + id), client2))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentSeesAllAndAssumesTicket() throws Exception {
        String client = register("Ana", "tk4@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tag2-tk@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client, "ALTA");

        mockMvc.perform(auth(get("/api/tickets"), agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.assignedToId").isNumber());
    }

    @Test
    void invalidTransitionIsConflict() throws Exception {
        String client = register("Ana", "tk5@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tag3-tk@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client, "BAIXA");

        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"RESOLVIDO"}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void clientCannotTransitionDirectly() throws Exception {
        String client = register("Ana", "tk6@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client, "BAIXA");
        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullLifecycleResolveAndClose() throws Exception {
        String client = register("Ana", "tk7@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tag4-tk@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client, "ALTA");

        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isOk());
        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"RESOLVIDO"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        // cliente (dono) pode fechar
        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"FECHADO"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"));
        // fechado é terminal
        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isConflict());
    }

    @Test
    void clientEditsOnlyWhileOpen() throws Exception {
        String client = register("Ana", "tk8@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tag5-tk@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client, "BAIXA");

        mockMvc.perform(auth(patch("/api/tickets/" + id), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Titulo novo"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo novo"));

        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isOk());

        mockMvc.perform(auth(patch("/api/tickets/" + id), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Tenta de novo"}
                                """)))
                .andExpect(status().isConflict());
    }

    @Test
    void filtersAndPaginationWork() throws Exception {
        String client = register("Ana", "tk9@dev.io", "Senha@123", "CLIENTE", null);
        newTicket(client, "BAIXA");
        mockMvc.perform(auth(post("/api/tickets"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Servidor sem acesso","description":"VPN bloqueada",
                                 "priority":"CRITICA"}
                                """)))
                .andExpect(status().isCreated());

        mockMvc.perform(auth(get("/api/tickets").param("priority", "CRITICA"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(auth(get("/api/tickets").param("status", "ABERTO"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(auth(get("/api/tickets").param("q", "Impressora"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(auth(get("/api/tickets").param("page", "0").param("size", "1"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void agentAssignsAndUnassigns() throws Exception {
        String client = register("Ana", "tk10@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tag6-tk@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client, "BAIXA");

        mockMvc.perform(auth(post("/api/tickets/" + id + "/assign"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").isNumber());

        mockMvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/tickets/" + id + "/assign"), agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").doesNotExist());
    }

    @Test
    void clientDeletesOnlyOwnOpenTicket() throws Exception {
        String client = register("Ana", "tk11@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client, "BAIXA");
        mockMvc.perform(auth(delete("/api/tickets/" + id), client))
                .andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTicketIs404() throws Exception {
        String client = register("Ana", "tk12@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(get("/api/tickets/999999"), client))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCreateBodyIs422() throws Exception {
        String client = register("Ana", "tk13@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(post("/api/tickets"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"","description":"","priority":null}
                                """)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations.title").exists())
                .andExpect(jsonPath("$.violations.priority").exists());
    }
}
