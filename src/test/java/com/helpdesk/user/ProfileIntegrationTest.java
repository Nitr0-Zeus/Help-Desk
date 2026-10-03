package com.helpdesk.user;

import com.helpdesk.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIntegrationTest extends IntegrationTestBase {

    @Test
    void returnsOwnProfile() throws Exception {
        String token = register("Ana", "p1@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(get("/api/users/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("p1@dev.io"))
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void updatesNameAndRejectsDuplicatedEmail() throws Exception {
        String token = register("Ana", "p2@dev.io", "Senha@123", "CLIENTE", null);
        register("Bia", "p3@dev.io", "Senha@123", "CLIENTE", null);

        mockMvc.perform(auth(patch("/api/users/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"name":"Ana Souza","email":"p2@dev.io"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Souza"));

        mockMvc.perform(auth(patch("/api/users/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"p3@dev.io"}
                                """)))
                .andExpect(status().isConflict());
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        String token = register("Ana", "p4@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(post("/api/users/me/password"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"currentPassword":"Errada@1","newPassword":"Nova@1234"}
                                """)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(auth(post("/api/users/me/password"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"currentPassword":"Senha@123","newPassword":"Nova@1234"}
                                """)))
                .andExpect(status().isNoContent());
        login("p4@dev.io", "Nova@1234");
    }

    @Test
    void deactivateDisablesLogin() throws Exception {
        String token = register("Ana", "p5@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(delete("/api/users/me"), token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"p5@dev.io","password":"Senha@123"}
                                """)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(auth(get("/api/users/me"), token))
                .andExpect(status().isOk());
    }

    @Test
    void invalidPasswordPayloadIs422() throws Exception {
        String token = register("Ana", "p6@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(post("/api/users/me/password"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"currentPassword":"Senha@123","newPassword":"1"}
                                """)))
                .andExpect(status().isUnprocessableEntity());
    }
}
