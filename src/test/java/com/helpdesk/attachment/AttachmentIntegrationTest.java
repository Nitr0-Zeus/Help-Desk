package com.helpdesk.attachment;

import com.helpdesk.IntegrationTestBase;
import com.helpdesk.auth.domain.InviteCode;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.InviteCodeRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentIntegrationTest extends IntegrationTestBase {

    @Autowired InviteCodeRepository inviteRepo;

    @BeforeEach
    void ensureInvite() {
        if (inviteRepo.findById("AGENTE-2026").isEmpty()) {
            inviteRepo.save(new InviteCode("AGENTE-2026", Role.AGENTE,
                    Instant.parse("2030-12-31T23:59:59Z")));
        }
    }

    private String newTicket(String token) throws Exception {
        var result = mockMvc.perform(auth(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/tickets"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"PC nao liga","description":"Led pisca e desliga",
                                 "priority":"ALTA"}
                                """)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private long commentWithAttachment(String token, String ticketId, MockMultipartFile file) throws Exception {
        var result = mockMvc.perform(auth(
                        multipart("/api/tickets/" + ticketId + "/comments")
                                .file(file)
                                .param("body", "Segue o log"), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].id").isNumber())
                .andExpect(jsonPath("$.attachments[0].filename").value("log.txt"))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asLong();
    }

    @Test
    void uploadsAndDownloadsAttachment() throws Exception {
        String client = register("Ana", "an1@dev.io", "Senha@123", "CLIENTE", null);
        String other = register("Bia", "an2@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tan1@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client);
        var file = new MockMultipartFile("files", "log.txt", MediaType.TEXT_PLAIN_VALUE,
                "linha 1 do log".getBytes());

        long attachmentId = commentWithAttachment(client, id, file);

        mockMvc.perform(auth(get("/api/attachments/" + attachmentId), client))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("log.txt")));

        // attachment is visible again on the timeline after a reload
        mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].attachments[0].id").value(attachmentId))
                .andExpect(jsonPath("$.content[0].attachments[0].filename").value("log.txt"));

        // agente tambem tem acesso ao chamado
        mockMvc.perform(auth(get("/api/attachments/" + attachmentId), agent))
                .andExpect(status().isOk());
        // outro cliente nao (404, nao vaza existencia)
        mockMvc.perform(auth(get("/api/attachments/" + attachmentId), other))
                .andExpect(status().isNotFound());
    }

    @Test
    void commentWithoutFilesStillWorks() throws Exception {
        String client = register("Ana", "an5@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        mockMvc.perform(auth(multipart("/api/tickets/" + id + "/comments")
                        .param("body", "Sem anexo"), client))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments").isEmpty())
                .andExpect(jsonPath("$.body").value("Sem anexo"));
    }

    @Test
    void rejectsDisallowedContentType() throws Exception {
        String client = register("Ana", "an3@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        var exe = new MockMultipartFile("files", "virus.exe", "application/x-msdownload",
                "MZ".getBytes());
        mockMvc.perform(auth(multipart("/api/tickets/" + id + "/comments")
                        .file(exe).param("body", "x"), client))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        String client = register("Ana", "an4@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        var big = new MockMultipartFile("files", "big.bin", MediaType.TEXT_PLAIN_VALUE,
                new byte[11 * 1024 * 1024]);
        mockMvc.perform(auth(multipart("/api/tickets/" + id + "/comments")
                        .file(big).param("body", "x"), client))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void blankCommentIs422() throws Exception {
        String client = register("Ana", "an6@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        mockMvc.perform(auth(multipart("/api/tickets/" + id + "/comments")
                        .param("body", "   "), client))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingAttachmentIs404() throws Exception {
        String client = register("Ana", "an7@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(get("/api/attachments/999999"), client))
                .andExpect(status().isNotFound());
    }

    @Test
    void commentsEndpointNowRequiresMultipart() throws Exception {
        String client = register("Ana", "an8@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        mockMvc.perform(auth(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/tickets/" + id + "/comments"),
                        client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"body":"json antigo"}
                                """)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void multipartWithoutBodyFieldIs400() throws Exception {
        String client = register("Ana", "an9@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        mockMvc.perform(auth(multipart("/api/tickets/" + id + "/comments"), client))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }
}
