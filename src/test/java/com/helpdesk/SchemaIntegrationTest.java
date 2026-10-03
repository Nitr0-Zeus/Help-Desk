package com.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIntegrationTest extends IntegrationTestBase {

    @Autowired JdbcTemplate jdbc;

    @Test
    void allTablesExist() {
        var tables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table'", String.class);
        assertThat(tables).contains("users", "invite_codes", "refresh_tokens",
                "sla_policies", "tickets", "timeline_entries", "attachments");
    }

    @Test
    void seedCreatesSlaPoliciesAndInvite() {
        Integer policies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sla_policies WHERE active = 1", Integer.class);
        assertThat(policies).isEqualTo(4);
        String invite = jdbc.queryForObject(
                "SELECT role FROM invite_codes WHERE code = 'AGENTE-2026'", String.class);
        assertThat(invite).isEqualTo("AGENTE");
    }
}
