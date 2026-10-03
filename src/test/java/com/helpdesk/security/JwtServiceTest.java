package com.helpdesk.security;

import com.helpdesk.auth.domain.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService service = new JwtService(
            "test-secret-0123456789abcdef0123456789abcdef", Duration.ofMinutes(15));

    private final Instant past = Instant.parse("2026-01-05T10:00:00Z");
    private final UserContext user = new UserContext(7L, "a@b.dev", Role.AGENTE);

    @Test
    void generatesAndVerifiesFreshToken() {
        String token = service.generateAccessToken(user, Instant.now());
        assertThat(service.verify(token)).contains(user);
    }

    @Test
    void rejectsExpiredToken() {
        String token = service.generateAccessToken(user, past);
        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.generateAccessToken(user, Instant.now()) + "x";
        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(service.verify("not-a-token")).isEmpty();
    }

    @Test
    void ttlSecondsIs900() {
        assertThat(service.accessTtlSeconds()).isEqualTo(900);
    }
}
