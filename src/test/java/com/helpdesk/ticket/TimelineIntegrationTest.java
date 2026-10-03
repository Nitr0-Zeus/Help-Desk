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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimelineIntegrationTest extends IntegrationTestBase {

    @Autowired InviteCodeRepository inviteRepo;

    @BeforeEach
    void ensureInvite() {
        if (inviteRepo.findById("AGENTE-2026").isEmpty()) {
            inviteRepo.save(new InviteCode("AGENTE-2026", Role.AGENTE,
                    Instant.parse("2030-12-31T23:59:59Z")));
        }
    }

    private String newTicket(String token) throws Exception {
        var result = mockMvc.perform(auth(post("/api/tickets"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Monitor piscando","description":"Liga e desliga sozinho",
                                 "priority":"MEDIA"}
                                """)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void transition(String token, String id, String status) throws Exception {
        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"status\":\"" + status + "\"}")))
                .andExpect(status().isOk());
    }

    private void comment(String token, String id, String body) throws Exception {
        mockMvc.perform(auth(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/tickets/" + id + "/comments")
                                .param("body", body), token))
                .andExpect(status().isCreated());
    }

    @Test
    void commentAndEventsAppearOnTimeline() throws Exception {
        String client = register("Ana", "tl1@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tal1@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client);

        comment(client, id, "Ja troquei o cabo de forca");
        transition(agent, id, "EM_ATENDIMENTO");

        mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("EVENT"))
                .andExpect(jsonPath("$.content[0].eventType").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.content[1].type").value("COMMENT"))
                .andExpect(jsonPath("$.content[1].body").value("Ja troquei o cabo de forca"))
                .andExpect(jsonPath("$.content[1].authorName").value("Ana"));
    }

    @Test
    void firstAgentCommentMarksFirstResponse() throws Exception {
        String client = register("Ana", "tl2@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tal2@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client);

        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(jsonPath("$.firstResponseAt").doesNotExist());

        comment(agent, id, "Verificando o equipamento");

        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(jsonPath("$.firstResponseAt").isNotEmpty());
    }

    @Test
    void waitingClientCommentResumesSla() throws Exception {
        String client = register("Ana", "tl3@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tal3@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");

        Instant created = Instant.parse("2026-01-05T12:00:00Z"); // seg 09:00 BRT
        when(time.now()).thenReturn(created);
        String id = newTicket(client);
        transition(agent, id, "EM_ATENDIMENTO");
        transition(agent, id, "AGUARDANDO_CLIENTE");

        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_CLIENTE"))
                .andExpect(jsonPath("$.waitingSince").isNotEmpty());

        // cliente responde 2h depois (horario util)
        when(time.now()).thenReturn(created.plusSeconds(2 * 3600L));
        comment(client, id, "Segue a foto do erro");

        mockMvc.perform(auth(get("/api/tickets/" + id), agent))
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.waitingSince").doesNotExist());

        mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), agent))
                .andExpect(jsonPath("$.content[0].eventType").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.content[1].eventType").value("SLA_RESUMED"));
    }

    @Test
    void breachIsFlaggedOnRead() throws Exception {
        String client = register("Ana", "tl4@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tal4@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");

        Instant created = Instant.parse("2026-01-05T12:00:00Z"); // seg 09:00 BRT
        when(time.now()).thenReturn(created);
        String id = newTicket(client);

        // MEDIA: primeira resposta vence 13:00 BRT; agora 16:00 BRT -> violado
        when(time.now()).thenReturn(Instant.parse("2026-01-05T19:00:00Z"));
        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaBreached").value(true));

        mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("SLA_BREACHED"));

        // segunda leitura nao duplica o evento
        mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), client))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void clientCommentsOnOpenTicketDoesNotChangeStatus() throws Exception {
        String client = register("Ana", "tl5@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tal5@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client);

        comment(client, id, "Alguma atualizacao?");
        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(jsonPath("$.status").value("ABERTO"));
    }

    @Test
    void otherClientCannotSeeTimeline() throws Exception {
        String client = register("Ana", "tl6@dev.io", "Senha@123", "CLIENTE", null);
        String other = register("Bia", "tl7@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);

        mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), other))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/tickets/" + id + "/comments")
                                .param("body", "invasao"), other))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankCommentIs422() throws Exception {
        String client = register("Ana", "tl8@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        mockMvc.perform(auth(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/tickets/" + id + "/comments")
                                .param("body", "   "), client))
                .andExpect(status().isUnprocessableEntity());
    }
}
