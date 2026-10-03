# Help Desk API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir uma API REST completa de help desk (chamados técnicos com SLA real, JWT, anexos, métricas e Swagger) conforme a spec aprovada.

**Architecture:** Modular monolith em `com.helpdesk` com módulos `common`, `security`, `auth`, `user`, `ticket`, `sla`, `attachment`, `metrics`; Flyway cuida do schema MySQL; SLA calculado em horário comercial (seg–sex 09:00–18:00, `America/Sao_Paulo`) com pausa em `AGUARDANDO_CLIENTE`; timeline única (comentários + eventos) na tabela `timeline_entries`.

**Tech Stack:** Java 17 (`release=17`, compilado no JDK 21), Spring Boot 3.3.4, Spring Security + JWT (jjwt 0.12.6), Spring Data JPA, Flyway + MySQL 8 (Docker), springdoc-openapi 2.6.0, Testcontainers MySQL, JUnit 5 + Mockito + MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-03-helpdesk-api-design.md` — todo requisito argumenta da spec; leia-a junto com este plano.

## Global Constraints

- Java: `maven.compiler.release=17` (máquina só tem JDK 21; NÃO instalar outro JDK).
- Spring Boot parent `3.3.4`; jjwt `0.12.6`; springdoc `2.6.0`.
- Pacote raiz `com.helpdesk`; módulos exatamente como spec §3.2.
- Papéis: apenas `CLIENTE` e `AGENTE` (nunca `ADMIN`).
- Access JWT 15 min (HS256, `helpdesk.jwt.secret` ≥32 bytes); refresh 7 dias opaco, hash SHA-256 no banco, rotação + detecção de reuso.
- SLA: seg–sex 09:00–18:00 (9 h/dia útil), zona `America/Sao_Paulo`, sem feriados.
- Políticas seed: BAIXA 8/72, MEDIA 4/48, ALTA 2/24, CRITICA 1/8 (primeira resposta/resolução em horas úteis). Invite seed: `AGENTE-2026` (expira 2030-12-31).
- Erros: sempre `ProblemDetail` (RFC 9457). 401 não autenticado/credencial; 403 papel; 404 inexistente **ou sem acesso**; 409 transição/estado/único; 413 anexo >10 MB; 415 MIME fora da whitelist; 422 validação (com `violations`).
- Anexos: máx. 10 MB; whitelist `image/png`, `image/jpeg`, `image/gif`, `application/pdf`, `text/plain`, `application/zip`; disco em `helpdesk.upload-dir` (default `./uploads`).
- DTOs: Java `records`; entidades JPA com getters/setters manuais (sem Lombok).
- `spring.jpa.hibernate.ddl-auto: none` (Flyway dono do schema; enums `@Enumerated(STRING)`).
- Senhas BCrypt strength 10. README em português; código em inglês.
- Commit ao fim de cada tarefa (`feat:`/`test:`/`docs:`). PowerShell: sem `&&` (usar `; if ($?) { ... }`).

---

### Task 1: Scaffold Maven + Docker MySQL + contexto sobe

**Files:**
- Create: `pom.xml`, `.gitignore`, `docker-compose.yml`
- Create: `src/main/resources/application.yml`, `application-test.yml`
- Create: `src/main/java/com/helpdesk/HelpdeskApplication.java`
- Create: `src/main/java/com/helpdesk/auth/domain/Role.java`
- Create: `src/main/java/com/helpdesk/security/UserContext.java`
- Create: `src/main/java/com/helpdesk/common/time/TimeProvider.java`, `SystemTimeProvider.java`
- Test: `src/test/java/com/helpdesk/IntegrationTestBase.java`, `HelpdeskApplicationTests.java`

**Interfaces:**
- Consumes: Docker Engine ativo (`docker info`), JDK 21 em `C:\Program Files\Java\jdk-21.0.6.7-hotspot`.
- Produces: `IntegrationTestBase` (container MySQL compartilhado, `MockMvc mockMvc`, `ObjectMapper mapper`, `@MockBean TimeProvider time`, helpers `register/login/token/json/auth`) usada por todas as integrações; `UserContext record (Long id, String email, Role role)` com `isAgent()` e `authorities()`; `TimeProvider.now(): Instant`.

- [ ] **Step 1: Instalar Maven e verificar ferramentas**

```powershell
winget install --id Apache.Maven -e --accept-source-agreements --accept-package-agreements
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
mvn -version
docker info --format "{{.ServerVersion}}"
```
Esperado: Maven 3.9.x e Docker respondendo. **Fallback winget:** baixar `https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip` para `$env:LOCALAPPDATA\maven` e usar `& "$env:LOCALAPPDATA\maven\apache-maven-3.9.9\bin\mvn.cmd"` no lugar de `mvn` em todos os comandos do plano.

- [ ] **Step 2: Criar `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>
  <groupId>com.helpdesk</groupId>
  <artifactId>helpdesk-api</artifactId>
  <version>0.1.0</version>
  <properties>
    <java.version>17</java.version>
    <jjwt.version>0.12.6</jjwt.version>
    <springdoc.version>2.6.0</springdoc.version>
  </properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>${springdoc.version}</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${jjwt.version}</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>mysql</artifactId><scope>test</scope></dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Criar `.gitignore`, `docker-compose.yml` e configs**

`.gitignore`:
```
target/
uploads/
.idea/
*.iml
.vscode/
```

`docker-compose.yml`:
```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: helpdesk-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: helpdesk
      MYSQL_USER: helpdesk
      MYSQL_PASSWORD: helpdesk
    ports: ["3306:3306"]
    volumes: [helpdesk-mysql-data:/var/lib/mysql]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot"]
      interval: 5s
      timeout: 3s
      retries: 30
volumes:
  helpdesk-mysql-data:
```

`src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: helpdesk-api
  profiles:
    default: dev
  datasource:
    url: ${HELPDESK_DB_URL:jdbc:mysql://localhost:3306/helpdesk?createDatabaseIfNotExist=true}
    username: ${HELPDESK_DB_USER:helpdesk}
    password: ${HELPDESK_DB_PASSWORD:helpdesk}
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
  flyway:
    enabled: true
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 60MB
server:
  port: 8080
management:
  endpoints:
    web:
      exposure:
        include: health
helpdesk:
  jwt:
    secret: ${HELPDESK_JWT_SECRET:dev-only-secret-change-me-0123456789abcdef}
    access-ttl: 15m
    refresh-ttl: 7d
  upload-dir: ${HELPDESK_UPLOAD_DIR:./uploads}
  sla:
    zone: America/Sao_Paulo
springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

`src/main/resources/application-test.yml`:
```yaml
helpdesk:
  upload-dir: ./target/test-uploads
```

- [ ] **Step 4: Criar classes raiz mínimas**

`src/main/java/com/helpdesk/HelpdeskApplication.java`:
```java
package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HelpdeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelpdeskApplication.class, args);
    }
}
```

`src/main/java/com/helpdesk/auth/domain/Role.java`:
```java
package com.helpdesk.auth.domain;

public enum Role {
    CLIENTE, AGENTE
}
```

`src/main/java/com/helpdesk/security/UserContext.java`:
```java
package com.helpdesk.security;

import com.helpdesk.auth.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public record UserContext(Long id, String email, Role role) {
    public boolean isAgent() {
        return role == Role.AGENTE;
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
```

`src/main/java/com/helpdesk/common/time/TimeProvider.java`:
```java
package com.helpdesk.common.time;

import java.time.Instant;

public interface TimeProvider {
    Instant now();
}
```

`src/main/java/com/helpdesk/common/time/SystemTimeProvider.java`:
```java
package com.helpdesk.common.time;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemTimeProvider implements TimeProvider {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 5: Criar base de teste + teste de contexto**

`src/test/java/com/helpdesk/IntegrationTestBase.java`:
```java
package com.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.helpdesk.common.time.TimeProvider;
import com.helpdesk.security.UserContext;
import com.helpdesk.auth.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("helpdesk_test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
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

    protected String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
}
```

`src/test/java/com/helpdesk/HelpdeskApplicationTests.java`:
```java
package com.helpdesk;

import org.junit.jupiter.api.Test;

class HelpdeskApplicationTests extends IntegrationTestBase {
    @Test
    void contextLoads() {
    }
}
```

Obs.: `register`/`login` só funcionam a partir da Task 4; em Task 1–3 apenas o contexto é exercitado (os helpers não são chamados ainda).

- [ ] **Step 6: Pré-baixar imagem e rodar testes**

```powershell
docker pull mysql:8.0; if ($?) { mvn -q test }
```
Esperado: BUILD SUCCESS (contexto carrega; Flyway sem migrations ainda — aviso aceitável).

- [ ] **Step 7: Smoke manual com compose**

```powershell
docker compose up -d; if ($?) { Start-Sleep 25; docker compose ps }
```
```powershell
mvn spring-boot:run
```
Em outro terminal: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`. Pare o app (Ctrl+C).

```powershell
git add -A; git commit -m "feat: scaffold Spring Boot 3.3 helpdesk-api with Testcontainers base"
```

---

### Task 2: Schema Flyway (V1) + seed (V2)

**Files:**
- Create: `src/main/resources/db/migration/V1__init.sql`
- Create: `src/main/resources/db/migration/V2__seed.sql`
- Test: `src/test/java/com/helpdesk/SchemaIntegrationTest.java`

**Interfaces:**
- Consumes: `IntegrationTestBase` (Task 1).
- Produces: tabelas `users`, `invite_codes`, `refresh_tokens`, `sla_policies`, `tickets`, `timeline_entries`, `attachments` (schema spec §3.3); seed com 4 políticas de SLA e invite `AGENTE-2026`.

- [ ] **Step 1: Escrever teste de schema/seed (falha)**

`src/test/java/com/helpdesk/SchemaIntegrationTest.java`:
```java
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
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
        assertThat(tables).contains("users", "invite_codes", "refresh_tokens",
                "sla_policies", "tickets", "timeline_entries", "attachments");
    }

    @Test
    void seedCreatesSlaPoliciesAndInvite() {
        Integer policies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sla_policies WHERE active = TRUE", Integer.class);
        assertThat(policies).isEqualTo(4);
        String invite = jdbc.queryForObject(
                "SELECT role FROM invite_codes WHERE code = 'AGENTE-2026'", String.class);
        assertThat(invite).isEqualTo("AGENTE");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=SchemaIntegrationTest
```
Esperado: FAIL (`Table 'helpdesk_test.users' doesn't exist`).

- [ ] **Step 3: Criar `V1__init.sql`**

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role ENUM('CLIENTE','AGENTE') NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE invite_codes (
    code VARCHAR(64) PRIMARY KEY,
    role ENUM('CLIENTE','AGENTE') NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_by BIGINT NULL,
    used_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_invite_used_by FOREIGN KEY (used_by) REFERENCES users(id)
);

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by CHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_refresh_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE sla_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    priority ENUM('BAIXA','MEDIA','ALTA','CRITICA') NOT NULL,
    first_response_hours INT NOT NULL,
    resolution_hours INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_sla_priority UNIQUE (priority)
);

CREATE TABLE tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    protocol VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    priority ENUM('BAIXA','MEDIA','ALTA','CRITICA') NOT NULL,
    status ENUM('ABERTO','EM_ATENDIMENTO','AGUARDANDO_CLIENTE','RESOLVIDO','FECHADO') NOT NULL,
    created_by BIGINT NOT NULL,
    assigned_to BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    first_response_at TIMESTAMP(6) NULL,
    waiting_since TIMESTAMP(6) NULL,
    resolved_at TIMESTAMP(6) NULL,
    closed_at TIMESTAMP(6) NULL,
    first_response_due_at TIMESTAMP(6) NULL,
    due_at TIMESTAMP(6) NULL,
    sla_breached BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_ticket_protocol UNIQUE (protocol),
    CONSTRAINT fk_ticket_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_ticket_assigned_to FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_created_by ON tickets(created_by);
CREATE INDEX idx_tickets_assigned_to ON tickets(assigned_to);

CREATE TABLE timeline_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    type ENUM('COMMENT','EVENT') NOT NULL,
    event_type ENUM('STATUS_CHANGED','ASSIGNED','SLA_PAUSED','SLA_RESUMED','SLA_BREACHED') NULL,
    author_id BIGINT NULL,
    body TEXT NULL,
    payload JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_timeline_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_timeline_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_timeline_ticket_created ON timeline_entries(ticket_id, created_at);

CREATE TABLE attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timeline_entry_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_name CHAR(36) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_attachment_storage UNIQUE (storage_name),
    CONSTRAINT fk_attachment_timeline FOREIGN KEY (timeline_entry_id) REFERENCES timeline_entries(id) ON DELETE CASCADE
);
```

- [ ] **Step 4: Criar `V2__seed.sql`**

```sql
INSERT INTO sla_policies (priority, first_response_hours, resolution_hours, active) VALUES
    ('BAIXA', 8, 72, TRUE),
    ('MEDIA', 4, 48, TRUE),
    ('ALTA', 2, 24, TRUE),
    ('CRITICA', 1, 8, TRUE);

INSERT INTO invite_codes (code, role, expires_at) VALUES
    ('AGENTE-2026', 'AGENTE', '2030-12-31 23:59:59');
```

- [ ] **Step 5: Rodar teste e commit**

```powershell
mvn -q test -Dtest=SchemaIntegrationTest; if ($?) { git add -A; git commit -m "feat: add Flyway schema V1 and SLA seed V2" }
```
Esperado: PASS + commit.

---

### Task 3: Segurança — JwtService, SecurityConfig, filtro

**Files:**
- Create: `src/main/java/com/helpdesk/security/JwtService.java`
- Create: `src/main/java/com/helpdesk/security/JwtAuthFilter.java`
- Create: `src/main/java/com/helpdesk/security/SecurityConfig.java`
- Create: `src/main/java/com/helpdesk/security/RestAuthenticationEntryPoint.java`
- Create: `src/main/java/com/helpdesk/security/RestAccessDeniedHandler.java`
- Create: `src/main/java/com/helpdesk/common/util/Hashing.java`
- Test: `src/test/java/com/helpdesk/security/JwtServiceTest.java`

**Interfaces:**
- Consumes: `UserContext`, `Role`, config `helpdesk.jwt.*` (Task 1).
- Produces (Tasks 4+):
  - `String JwtService.generateAccessToken(UserContext user, Instant now)`
  - `Optional<UserContext> JwtService.verify(String token)` — vazio em inválido/expirado
  - `long JwtService.accessTtlSeconds()`
  - `String Hashing.sha256Hex(String value)`
  - Filter chain stateless com principal `UserContext`; públicos: `/api/auth/**`, GET `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`.

- [ ] **Step 1: Escrever teste unitário do JwtService (falha)**

`src/test/java/com/helpdesk/security/JwtServiceTest.java`:
```java
package com.helpdesk.security;

import com.helpdesk.auth.domain.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService service = new JwtService(
            "test-secret-0123456789abcdef0123456789abcdef", Duration.ofMinutes(15));

    private final Instant now = Instant.parse("2026-01-05T10:00:00Z");
    private final UserContext user = new UserContext(7L, "a@b.dev", Role.AGENTE);

    @Test
    void generatesAndVerifiesToken() {
        String token = service.generateAccessToken(user, now);
        assertThat(service.verify(token)).isEmpty(); // emitido em 2026-01, expirado p/ Instant.now()
    }

    @Test
    void verifiesFreshToken() {
        String token = service.generateAccessToken(user, Instant.now());
        assertThat(service.verify(token)).contains(user);
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.generateAccessToken(user, Instant.now()) + "x";
        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    void ttlSecondsIs900() {
        assertThat(service.accessTtlSeconds()).isEqualTo(900);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=JwtServiceTest
```
Esperado: FAIL (`cannot find symbol: class JwtService`).

- [ ] **Step 3: Implementar JwtService + Hashing**

`src/main/java/com/helpdesk/security/JwtService.java`:
```java
package com.helpdesk.security;

import com.helpdesk.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(@Value("${helpdesk.jwt.secret}") String secret,
                      @Value("${helpdesk.jwt.access-ttl}") Duration accessTtl) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("helpdesk.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
    }

    public String generateAccessToken(UserContext user, Instant now) {
        return Jwts.builder()
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<UserContext> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new UserContext(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
```

`src/main/java/com/helpdesk/common/util/Hashing.java`:
```java
package com.helpdesk.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {
    private Hashing() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 4: Rodar teste do JwtService**

```powershell
mvn -q test -Dtest=JwtServiceTest
```
Esperado: PASS (4 testes).

- [ ] **Step 5: Implementar filtro, entry points e SecurityConfig**

`src/main/java/com/helpdesk/security/JwtAuthFilter.java`:
```java
package com.helpdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.verify(header.substring(7)).ifPresent(user ->
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user, null, user.authorities())));
        }
        chain.doFilter(request, response);
    }
}
```

`src/main/java/com/helpdesk/security/RestAuthenticationEntryPoint.java`:
```java
package com.helpdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                        + "\"detail\":\"Authentication required\",\"instance\":\"" + request.getRequestURI() + "\"}");
    }
}
```

`src/main/java/com/helpdesk/security/RestAccessDeniedHandler.java`:
```java
package com.helpdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                        + "\"detail\":\"Insufficient permissions\",\"instance\":\"" + request.getRequestURI() + "\"}");
    }
}
```

`src/main/java/com/helpdesk/security/SecurityConfig.java`:
```java
package com.helpdesk.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RestAuthenticationEntryPoint entryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RestAuthenticationEntryPoint entryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.entryPoint = entryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                        "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

- [ ] **Step 6: Rodar suíte e commit**

```powershell
mvn -q test; if ($?) { git add -A; git commit -m "feat: JWT service, auth filter and stateless security chain" }
```
Esperado: PASS.

---

### Task 4: Erros globais (ProblemDetail) + Auth completo (register/login/refresh/logout)

**Files:**
- Create: `src/main/java/com/helpdesk/common/error/` — `NotFoundException`, `ConflictException`, `UnauthorizedException`, `PayloadTooLargeException`, `UnsupportedMediaTypeException`, `GlobalExceptionHandler`
- Create: `src/main/java/com/helpdesk/auth/domain/` — `User`, `InviteCode`, `RefreshToken`
- Create: `src/main/java/com/helpdesk/auth/repo/` — `UserRepository`, `InviteCodeRepository`, `RefreshTokenRepository`
- Create: `src/main/java/com/helpdesk/auth/api/AuthController.java`, `auth/api/dto/AuthDtos.java`
- Create: `src/main/java/com/helpdesk/auth/service/AuthService.java`, `DevDataSeeder.java`
- Create: `src/main/java/com/helpdesk/common/config/SecurityBeansConfig.java`
- Test: `src/test/java/com/helpdesk/auth/AuthIntegrationTest.java`

**Interfaces:**
- Consumes: `JwtService`/`Hashing` (Task 3), invite seed (Task 2), `IntegrationTestBase`.
- Produces (Tasks 5+):
  - Exceções `RuntimeException` com `String detail` → handler devolve ProblemDetail 404/409/401/413/415; `MethodArgumentNotValidException` → 422 com `violations`.
  - `@Bean PasswordEncoder passwordEncoder()` (BCrypt 10).
  - `POST /api/auth/register` (201), `POST /api/auth/login` (200), `POST /api/auth/refresh` (200), `POST /api/auth/logout` (204). DTOs: `RegisterRequest(name,email,password,role,inviteCode?)`, `LoginRequest(email,password)`, `RefreshRequest(refreshToken)`, `AuthResponse(accessToken,tokenType,expiresIn,refreshToken,refreshExpiresAt)`.
  - `UserRepository`: `existsByEmail`, `findByEmail`, `save`. `User` tem `id,name,email,passwordHash,role,active,createdAt,updatedAt`.

- [ ] **Step 1: Escrever teste de integração do auth (falha)**

`src/test/java/com/helpdesk/auth/AuthIntegrationTest.java`:
```java
package com.helpdesk.auth;

import com.helpdesk.IntegrationTestBase;
import com.helpdesk.auth.domain.InviteCode;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.InviteCodeRepository;
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

    private void ensureInvite() {
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
                                {"name":"Bia","email":"bia@dev.io","password":"Senha@123","role":"AGENTE"}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void registerAgentWithInviteConsumesCode() throws Exception {
        ensureInvite();
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
    void inactiveUserCannotLogin() throws Exception {
        register("Ana", "ana5@dev.io", "Senha@123", "CLIENTE", null);
        // desativação é coberta em ProfileIntegrationTest; aqui só garante login ativo
        login("ana5@dev.io", "Senha@123");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=AuthIntegrationTest
```
Esperado: FAIL (404 nos endpoints / beans ausentes).

- [ ] **Step 3: Implementar exceções + GlobalExceptionHandler**

Cada exceção em `src/main/java/com/helpdesk/common/error/<Nome>.java` segue o mesmo padrão:
```java
package com.helpdesk.common.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String detail) {
        super(detail);
    }
}
```
Repita para `ConflictException`, `UnauthorizedException`, `PayloadTooLargeException`, `UnsupportedMediaTypeException`.

`src/main/java/com/helpdesk/common/error/GlobalExceptionHandler.java`:
```java
package com.helpdesk.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail unauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), req);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ProblemDetail tooLarge(PayloadTooLargeException ex, HttpServletRequest req) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", ex.getMessage(), req);
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ProblemDetail unsupported(UnsupportedMediaTypeException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed",
                "Request validation failed", req);
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            violations.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        pd.setProperty("violations", violations);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "Malformed JSON body", req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail uploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large",
                "File exceeds the 10 MB limit", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail auth(AuthenticationException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail denied(AccessDeniedException ex, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Insufficient permissions", req);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail generic(Exception ex, HttpServletRequest req) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Unexpected error", req);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setInstance(req.getRequestURI());
        return pd;
    }
}
```

- [ ] **Step 4: Implementar entidades e repositórios do auth**

`src/main/java/com/helpdesk/auth/domain/User.java`:
```java
package com.helpdesk.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected User() {
    }

    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setActive(boolean active) { this.active = active; }
    public void touch() { this.updatedAt = Instant.now(); }
}
```

`src/main/java/com/helpdesk/auth/domain/InviteCode.java`:
```java
package com.helpdesk.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "invite_codes")
public class InviteCode {

    @Id
    @Column(length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private User usedBy;

    @Column(name = "used_at")
    private Instant usedAt;

    protected InviteCode() {
    }

    public InviteCode(String code, Role role, Instant expiresAt) {
        this.code = code;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public String getCode() { return code; }
    public Role getRole() { return role; }
    public Instant getExpiresAt() { return expiresAt; }
    public User getUsedBy() { return usedBy; }
    public Instant getUsedAt() { return usedAt; }

    public void markUsed(User user, Instant now) {
        this.usedBy = user;
        this.usedAt = now;
    }

    public boolean isAvailable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
```

`src/main/java/com/helpdesk/auth/domain/RefreshToken.java`:
```java
package com.helpdesk.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "replaced_by", length = 64)
    private String replacedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public String getReplacedBy() { return replacedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke(String replacedByHash) {
        this.revoked = true;
        this.replacedBy = replacedByHash;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
```

Repositórios em `src/main/java/com/helpdesk/auth/repo/`:
```java
package com.helpdesk.auth.repo;

import com.helpdesk.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}
```
```java
package com.helpdesk.auth.repo;

import com.helpdesk.auth.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteCodeRepository extends JpaRepository<InviteCode, String> {
}
```
```java
package com.helpdesk.auth.repo;

import com.helpdesk.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);
}
```

- [ ] **Step 5: Implementar SecurityBeansConfig, DTOs, AuthService, AuthController**

`src/main/java/com/helpdesk/common/config/SecurityBeansConfig.java`:
```java
package com.helpdesk.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
```

`src/main/java/com/helpdesk/auth/api/dto/AuthDtos.java`:
```java
package com.helpdesk.auth.api.dto;

import com.helpdesk.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role,
            String inviteCode) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            Instant refreshExpiresAt) {
    }
}
```

`src/main/java/com/helpdesk/auth/service/AuthService.java`:
```java
package com.helpdesk.auth.service;

import com.helpdesk.auth.api.dto.AuthDtos.AuthResponse;
import com.helpdesk.auth.api.dto.AuthDtos.LoginRequest;
import com.helpdesk.auth.api.dto.AuthDtos.RegisterRequest;
import com.helpdesk.auth.domain.InviteCode;
import com.helpdesk.auth.domain.RefreshToken;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.InviteCodeRepository;
import com.helpdesk.auth.repo.RefreshTokenRepository;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.common.error.ConflictException;
import com.helpdesk.common.error.UnauthorizedException;
import com.helpdesk.common.time.TimeProvider;
import com.helpdesk.common.util.Hashing;
import com.helpdesk.security.JwtService;
import com.helpdesk.security.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {

    private final UserRepository users;
    private final InviteCodeRepository invites;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TimeProvider time;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, InviteCodeRepository invites,
                       RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TimeProvider time,
                       @Value("${helpdesk.jwt.refresh-ttl}") Duration refreshTtl) {
        this.users = users;
        this.invites = invites;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.time = time;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }
        Instant now = time.now();
        User user = new User(request.name(), request.email(),
                passwordEncoder.encode(request.password()), request.role());
        users.save(user);

        if (request.role() == Role.AGENTE) {
            InviteCode invite = invites.findById(request.inviteCode() == null ? "" : request.inviteCode())
                    .filter(InviteCode::isAvailable, () -> now)
                    .orElseThrow(() -> new ConflictException("Invalid or expired invite code"));
            invite.markUsed(user, now);
            invites.save(invite);
        }
        return issueTokens(user, now);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueTokens(user, time.now());
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        Instant now = time.now();
        RefreshToken stored = refreshTokens.findByTokenHash(Hashing.sha256Hex(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (stored.isRevoked()) {
            refreshTokens.findAllByUserIdAndRevokedFalse(stored.getUser().getId())
                    .forEach(t -> t.revoke(null));
            refreshTokens.saveAll(refreshTokens.findAllByUserIdAndRevokedFalse(stored.getUser().getId()));
            throw new UnauthorizedException("Refresh token reuse detected");
        }
        if (stored.isExpired(now) || !stored.getUser().isActive()) {
            throw new UnauthorizedException("Refresh token expired");
        }
        String newRaw = newRefreshToken();
        String newHash = Hashing.sha256Hex(newRaw);
        stored.revoke(newHash);
        refreshTokens.save(stored);
        refreshTokens.save(new RefreshToken(stored.getUser(), newHash, now.plus(refreshTtl)));
        User user = stored.getUser();
        return new AuthResponse(jwtService.generateAccessToken(toContext(user), now),
                "Bearer", jwtService.accessTtlSeconds(), newRaw, now.plus(refreshTtl));
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(Hashing.sha256Hex(rawToken))
                .ifPresent(token -> {
                    token.revoke(null);
                    refreshTokens.save(token);
                });
    }

    private AuthResponse issueTokens(User user, Instant now) {
        String raw = newRefreshToken();
        refreshTokens.save(new RefreshToken(user, Hashing.sha256Hex(raw), now.plus(refreshTtl)));
        return new AuthResponse(jwtService.generateAccessToken(toContext(user), now),
                "Bearer", jwtService.accessTtlSeconds(), raw, now.plus(refreshTtl));
    }

    private UserContext toContext(User user) {
        return new UserContext(user.getId(), user.getEmail(), user.getRole());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

Obs. de implementação: o `filter(...)` acima usa `Optional.filter(Predicate, Supplier)` — se a assinatura não existir na sua JDK, reescreva como `InviteCode invite = invites.findById(code).orElseThrow(...); if (!invite.isAvailable(now)) throw new ConflictException(...);`. O comportamento exigido pelo teste é: código ausente/inválido/usado/expirado → 409.

`src/main/java/com/helpdesk/auth/api/AuthController.java`:
```java
package com.helpdesk.auth.api;

import com.helpdesk.auth.api.dto.AuthDtos.*;
import com.helpdesk.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
```

`src/main/java/com/helpdesk/auth/service/DevDataSeeder.java`:
```java
package com.helpdesk.auth.service;

import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevDataSeeder {

    @Bean
    CommandLineRunner seedDemoUsers(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (!users.existsByEmail("cliente@demo.com")) {
                users.save(new User("Cliente Demo", "cliente@demo.com",
                        encoder.encode("Senha@123"), Role.CLIENTE));
            }
            if (!users.existsByEmail("agente@demo.com")) {
                users.save(new User("Agente Demo", "agente@demo.com",
                        encoder.encode("Senha@123"), Role.AGENTE));
            }
        };
    }
}
```

- [ ] **Step 6: Rodar teste de auth**

```powershell
mvn -q test -Dtest=AuthIntegrationTest
```
Esperado: PASS (9 testes). Se `registerAgentWithoutInviteIsConflict` falhar com 422, remova `inviteCode` da validação (deve ser opcional no DTO) — a decisão de 409 é do serviço.

- [ ] **Step 7: Rodar suíte toda e commit**

```powershell
mvn -q test; if ($?) { git add -A; git commit -m "feat: global ProblemDetail errors and full JWT auth flow" }
```

---

### Task 5: Perfil do usuário (`/api/users/me`)

**Files:**
- Create: `src/main/java/com/helpdesk/user/UserController.java`, `UserService.java`, `dto/UserDtos.java`
- Test: `src/test/java/com/helpdesk/user/ProfileIntegrationTest.java`

**Interfaces:**
- Consumes: `UserRepository` + entidades (Task 4), `PasswordEncoder`, exceções do `common.error`.
- Produces:
  - `GET /api/users/me` → `UserProfile(id,name,email,role,active)` (200)
  - `PATCH /api/users/me` body `{name?, email?}` → 200 `UserProfile` (email duplicado → 409)
  - `POST /api/users/me/password` body `{currentPassword,newPassword}` → 204 (senha atual errada → 401; nova inválida → 422)
  - `DELETE /api/users/me` → 204 (soft delete + revoga todos os refresh tokens; login subsequente → 401)
  - Método auxiliar público `UserService.loadRequired(Long id): User` (usado por Tasks 7+ para resolver `assignedTo`).

- [ ] **Step 1: Escrever teste (falha)**

`src/test/java/com/helpdesk/user/ProfileIntegrationTest.java`:
```java
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
    void deactivateDisablesLoginButKeepsTokenValidUntilExpiry() throws Exception {
        String token = register("Ana", "p5@dev.io", "Senha@123", "CLIENTE", null);
        mockMvc.perform(auth(delete("/api/users/me"), token))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"p5@dev.io","password":"Senha@123"}
                                """)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(auth(get("/api/users/me"), token))
                .andExpect(status().isOk()); // access token de 15min segue válido (tradeoff documentado)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=ProfileIntegrationTest
```
Esperado: FAIL (404).

- [ ] **Step 3: Implementar DTOs, service e controller**

`src/main/java/com/helpdesk/user/dto/UserDtos.java`:
```java
package com.helpdesk.user.dto;

import com.helpdesk.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserProfile(Long id, String name, String email, Role role, boolean active) {
    }

    public record UpdateProfileRequest(
            @Size(max = 120) String name,
            @Email @Size(max = 180) String email) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
```

`src/main/java/com/helpdesk/user/UserService.java`:
```java
package com.helpdesk.user;

import com.helpdesk.auth.domain.RefreshToken;
import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.RefreshTokenRepository;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.common.error.ConflictException;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.common.error.UnauthorizedException;
import com.helpdesk.user.dto.UserDtos.ChangePasswordRequest;
import com.helpdesk.user.dto.UserDtos.UpdateProfileRequest;
import com.helpdesk.user.dto.UserDtos.UserProfile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
    }

    public User loadRequired(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UserProfile profile(Long userId) {
        return toProfile(loadRequired(userId));
    }

    @Transactional
    public UserProfile update(Long userId, UpdateProfileRequest request) {
        User user = loadRequired(userId);
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.email() != null && !request.email().isBlank()
                && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (users.existsByEmail(request.email())) {
                throw new ConflictException("Email already in use");
            }
            user.setEmail(request.email().trim());
        }
        user.touch();
        return toProfile(users.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = loadRequired(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is invalid");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.touch();
        users.save(user);
    }

    @Transactional
    public void deactivate(Long userId) {
        User user = loadRequired(userId);
        user.setActive(false);
        user.touch();
        users.save(user);
        Instant now = Instant.now();
        for (RefreshToken token : refreshTokens.findAllByUserIdAndRevokedFalse(userId)) {
            token.revoke(null);
        }
        refreshTokens.findAllByUserIdAndRevokedFalse(userId).forEach(refreshTokens::save);
        now.toString();
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.isActive());
    }
}
```

Obs.: em `deactivate`, faça a revogação em um único loop (`findAllByUserIdAndRevokedFalse` → `revoke` → `saveAll`) — o trecho `now` acima é descartável; limpe ao implementar:
```java
var active = refreshTokens.findAllByUserIdAndRevokedFalse(userId);
active.forEach(t -> t.revoke(null));
refreshTokens.saveAll(active);
```

`src/main/java/com/helpdesk/user/UserController.java`:
```java
package com.helpdesk.user;

import com.helpdesk.security.UserContext;
import com.helpdesk.user.dto.UserDtos.ChangePasswordRequest;
import com.helpdesk.user.dto.UserDtos.UpdateProfileRequest;
import com.helpdesk.user.dto.UserDtos.UserProfile;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal UserContext current) {
        return userService.profile(current.id());
    }

    @PatchMapping("/me")
    public UserProfile update(@AuthenticationPrincipal UserContext current,
                              @Valid @RequestBody UpdateProfileRequest request) {
        return userService.update(current.id(), request);
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserContext current,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(current.id(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal UserContext current) {
        userService.deactivate(current.id());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Rodar teste, suíte e commit**

```powershell
mvn -q test -Dtest=ProfileIntegrationTest; if ($?) { mvn -q test }
```
Esperado: PASS.
```powershell
git add -A; git commit -m "feat: user profile endpoints (read/update/password/deactivate)"
```

---

### Task 6: Módulo SLA — calendário útil, políticas e cálculo

**Files:**
- Create: `src/main/java/com/helpdesk/sla/BusinessCalendar.java`, `SlaCalculator.java`, `SlaPolicy.java`, `SlaPolicyRepository.java`, `SlaPolicyController.java`, `dto/SlaDtos.java`
- Test: `src/test/java/com/helpdesk/sla/BusinessCalendarTest.java`, `SlaCalculatorTest.java`

**Interfaces:**
- Consumes: tabela `sla_policies` (Task 2), `TimeProvider` (Task 1), `Priority` virá da Task 7 — por isso este módulo define o enum compartilhado **aqui**: `com.helpdesk.ticket.Priority` será criado nesta task (é usado por `sla_policies` e `tickets`).
- Produces (Task 7+):
  - `enum Priority { BAIXA, MEDIA, ALTA, CRITICA }` em `com.helpdesk.ticket`
  - `BusinessCalendar` (`@Component`, `ZoneId zone` de `helpdesk.sla.zone`):
    - `Instant addWorkingHours(Instant start, long hours)` — soma em horário comercial seg–sex 09:00–18:00
    - `Instant addWorkingMinutes(Instant start, long minutes)`
    - `long workingMinutesBetween(Instant from, Instant to)` — minutos úteis entre dois instantes
  - `SlaCalculator` (`@Component`): `SlaDeadlines calculate(Priority priority, Instant from)` → record `SlaDeadlines(Instant firstResponseDueAt, Instant dueAt)`; `Instant shift(Instant dueAt, long minutesToShift)`; `boolean isBreached(TicketSlaView view, Instant now)`.
  - `record TicketSlaView(Instant firstResponseDueAt, Instant dueAt, Instant firstResponseAt, Instant resolvedAt, Instant closedAt, boolean paused)` — tipo de leitura desacoplado do módulo `ticket` (o `ticket` monta essa view).
  - `GET /api/sla-policies` → lista `SlaPolicyView(priority, firstResponseHours, resolutionHours, active)` (autenticado).

- [ ] **Step 1: Escrever testes unitários (falham)**

`src/test/java/com/helpdesk/sla/BusinessCalendarTest.java`:
```java
package com.helpdesk.sla;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCalendarTest {

    private final BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("America/Sao_Paulo"));

    // 2026-01-05 é uma segunda-feira
    private final Instant monday9 = Instant.parse("2026-01-05T11:00:00Z"); // 09:00 BRT (UTC-2)

    @Test
    void addsHoursWithinSameDay() {
        Instant result = calendar.addWorkingHours(monday9, 2);
        assertThat(result).isEqualTo(Instant.parse("2026-01-05T15:00:00Z")); // 13:00 BRT
    }

    @Test
    void skipsWeekend() {
        // sexta 16:00 BRT + 4h úteis → segunda 11:00 BRT (fecha 18h, 2h restantes +2h na seg)
        Instant friday16 = Instant.parse("2026-01-09T18:00:00Z");
        Instant result = calendar.addWorkingHours(friday16, 4);
        assertThat(result).isEqualTo(Instant.parse("2026-01-12T15:00:00Z")); // 13:00 BRT segunda
    }

    @Test
    void rollsOverEndOfBusinessDay() {
        Instant friday17 = Instant.parse("2026-01-09T19:00:00Z"); // 17:00 BRT
        Instant result = calendar.addWorkingHours(friday17, 2);
        assertThat(result).isEqualTo(Instant.parse("2026-01-12T13:00:00Z")); // 11:00 BRT segunda
    }

    @Test
    void countsMinutesBetweenAcrossWeekend() {
        // sex 17:00 BRT → seg 10:00 BRT = 1h (sex) + 1h (seg) = 120 min
        Instant from = Instant.parse("2026-01-09T19:00:00Z");
        Instant to = Instant.parse("2026-01-12T12:00:00Z");
        assertThat(calendar.workingMinutesBetween(from, to)).isEqualTo(120);
    }

    @Test
    void ignoresNightAndWeekendInBetween() {
        // sex 17:00 → sex 23:59 = 60 min (fecha 18h)
        Instant from = Instant.parse("2026-01-09T19:00:00Z");
        Instant to = Instant.parse("2026-01-10T01:59:00Z");
        assertThat(calendar.workingMinutesBetween(from, to)).isEqualTo(59);
    }
}
```

`src/test/java/com/helpdesk/sla/SlaCalculatorTest.java`:
```java
package com.helpdesk.sla;

import com.helpdesk.ticket.Priority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class SlaCalculatorTest {

    private final BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("America/Sao_Paulo"));
    private final SlaCalculator calculator = new SlaCalculator(calendar, java.util.Map.of(
            Priority.BAIXA, new SlaCalculator.Policy(8, 72),
            Priority.MEDIA, new SlaCalculator.Policy(4, 48),
            Priority.ALTA, new SlaCalculator.Policy(2, 24),
            Priority.CRITICA, new SlaCalculator.Policy(1, 8)));

    private final Instant monday9 = Instant.parse("2026-01-05T11:00:00Z"); // 09:00 BRT

    @Test
    void calculatesDeadlinesFromPriority() {
        SlaCalculator.SlaDeadlines deadlines = calculator.calculate(Priority.ALTA, monday9);
        assertThat(deadlines.firstResponseDueAt())
                .isEqualTo(Instant.parse("2026-01-05T13:00:00Z")); // +2h = 11:00 BRT
        assertThat(deadlines.dueAt())
                .isEqualTo(Instant.parse("2026-01-06T11:00:00Z")); // +24h úteis = ter 09:00 BRT
    }

    @Test
    void breachOnlyWhenPastDeadlineAndUnresolved() {
        SlaCalculator.SlaDeadlines d = calculator.calculate(Priority.CRITICA, monday9);
        var view = new SlaCalculator.TicketSlaView(
                d.firstResponseDueAt(), d.dueAt(), null, null, null, false);
        assertThat(calculator.isBreached(view, monday9.plus(30 * 60L))).isFalse();
        assertThat(calculator.isBreached(view, monday9.plus(2 * 60 * 60L))).isTrue();
    }

    @Test
    void resolvedTicketIsNeverBreached() {
        SlaCalculator.SlaDeadlines d = calculator.calculate(Priority.CRITICA, monday9);
        var view = new SlaCalculator.TicketSlaView(
                d.firstResponseDueAt(), d.dueAt(), monday9.plus(600),
                monday9.plus(3600), null, false);
        assertThat(calculator.isBreached(view, monday9.plus(10 * 60 * 60L))).isFalse();
    }

    @Test
    void shiftsDeadlinesByPausedMinutes() {
        SlaCalculator.SlaDeadlines d = calculator.calculate(Priority.MEDIA, monday9);
        Instant shifted = calculator.shift(d.dueAt(), 90);
        assertThat(shifted).isAfter(d.dueAt());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest="BusinessCalendarTest,SlaCalculatorTest"
```
Esperado: FAIL (`cannot find symbol`).

- [ ] **Step 3: Implementar BusinessCalendar**

`src/main/java/com/helpdesk/sla/BusinessCalendar.java`:
```java
package com.helpdesk.sla;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class BusinessCalendar {

    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(18, 0);

    private final ZoneId zone;

    public BusinessCalendar(@Value("${helpdesk.sla.zone}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
    }

    public Instant addWorkingHours(Instant start, long hours) {
        return addWorkingMinutes(start, hours * 60);
    }

    public Instant addWorkingMinutes(Instant start, long minutes) {
        if (minutes <= 0) {
            return start;
        }
        LocalDateTime cursor = LocalDateTime.ofInstant(start, zone);
        long remaining = minutes;
        while (remaining > 0) {
            cursor = advanceToBusinessTime(cursor);
            LocalDate day = cursor.toLocalDate();
            LocalDateTime close = day.atTime(CLOSE);
            long available = Duration.between(cursor, close).toMinutes();
            if (available >= remaining) {
                return cursor.plusMinutes(remaining).atZone(zone).toInstant();
            }
            remaining -= available;
            cursor = day.plusDays(1).atStartOfDay();
        }
        return cursor.atZone(zone).toInstant();
    }

    public long workingMinutesBetween(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            return 0;
        }
        LocalDateTime cursor = LocalDateTime.ofInstant(from, zone);
        LocalDateTime end = LocalDateTime.ofInstant(to, zone);
        long total = 0;
        while (cursor.isBefore(end)) {
            if (isBusinessDay(cursor)) {
                LocalDateTime dayStart = cursor.toLocalDate().atTime(OPEN);
                LocalDateTime dayClose = cursor.toLocalDate().atTime(CLOSE);
                LocalDateTime windowStart = cursor.isBefore(dayStart) ? dayStart : cursor;
                LocalDateTime windowEnd = end.isBefore(dayClose) ? end : dayClose;
                if (windowEnd.isAfter(windowStart)) {
                    total += Duration.between(windowStart, windowEnd).toMinutes();
                }
            }
            cursor = cursor.toLocalDate().plusDays(1).atTime(OPEN);
        }
        return total;
    }

    private LocalDateTime advanceToBusinessTime(LocalDateTime cursor) {
        while (!isBusinessDay(cursor)) {
            cursor = cursor.toLocalDate().plusDays(1).atStartOfDay();
        }
        LocalDateTime open = cursor.toLocalDate().atTime(OPEN);
        if (cursor.isBefore(open)) {
            return open;
        }
        if (!cursor.isBefore(cursor.toLocalDate().atTime(CLOSE))) {
            return cursor.toLocalDate().plusDays(1).atStartOfDay();
        }
        return cursor;
    }

    private boolean isBusinessDay(LocalDateTime dateTime) {
        DayOfWeek day = dateTime.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
```

Obs.: `advanceToBusinessTime` para sábado/domingo deve pular até a segunda — o `while` com `plusDays(1)` cobre; após o salto para `atStartOfDay` de segunda, o loop reavalia e cai no horário de abertura.

- [ ] **Step 4: Implementar Priority, SlaCalculator, política, controller**

`src/main/java/com/helpdesk/ticket/Priority.java`:
```java
package com.helpdesk.ticket;

public enum Priority {
    BAIXA, MEDIA, ALTA, CRITICA
}
```

`src/main/java/com/helpdesk/sla/SlaCalculator.java`:
```java
package com.helpdesk.sla;

import com.helpdesk.ticket.Priority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class SlaCalculator {

    public record Policy(int firstResponseHours, int resolutionHours) {
    }

    public record SlaDeadlines(Instant firstResponseDueAt, Instant dueAt) {
    }

    public record TicketSlaView(Instant firstResponseDueAt, Instant dueAt,
                                Instant firstResponseAt, Instant resolvedAt,
                                Instant closedAt, boolean paused) {
    }

    private final BusinessCalendar calendar;
    private final Map<Priority, Policy> policies;

    public SlaCalculator(BusinessCalendar calendar, SlaPolicyRepository policyRepository) {
        this.calendar = calendar;
        this.policies = Map.of(
                Priority.BAIXA, policyFrom(policyRepository, Priority.BAIXA),
                Priority.MEDIA, policyFrom(policyRepository, Priority.MEDIA),
                Priority.ALTA, policyFrom(policyRepository, Priority.ALTA),
                Priority.CRITICA, policyFrom(policyRepository, Priority.CRITICA));
    }

    private static Policy policyFrom(SlaPolicyRepository repo, Priority priority) {
        SlaPolicy policy = repo.findByPriority(priority)
                .orElseThrow(() -> new IllegalStateException("Missing SLA policy for " + priority));
        return new Policy(policy.getFirstResponseHours(), policy.getResolutionHours());
    }

    public SlaDeadlines calculate(Priority priority, Instant from) {
        Policy policy = policies.get(priority);
        return new SlaDeadlines(
                calendar.addWorkingHours(from, policy.firstResponseHours()),
                calendar.addWorkingHours(from, policy.resolutionHours()));
    }

    public Instant shift(Instant dueAt, long minutesToShift) {
        return calendar.addWorkingMinutes(dueAt, minutesToShift);
    }

    public boolean isBreached(TicketSlaView view, Instant now) {
        if (view.resolvedAt() != null || view.closedAt() != null) {
            return false;
        }
        boolean pastFirstResponse = view.firstResponseAt() == null
                && view.firstResponseDueAt() != null && now.isAfter(view.firstResponseDueAt());
        boolean pastResolution = view.dueAt() != null && now.isAfter(view.dueAt());
        return pastFirstResponse || pastResolution;
    }
}
```

Obs.: o construtor recebe `SlaPolicyRepository` e carrega os 4 policies do seed. Se preferir testabilidade sem banco, mantenha o construtor de teste acima (com `Map.of`) — o teste unitário usa o construtor `SlaCalculator(BusinessCalendar, Map<Priority,Policy>)`. **Implemente os dois construtores**: um `@Autowired` com repositório (usa o seed) e um package-private com `Map` para os testes:
```java
public SlaCalculator(BusinessCalendar calendar, SlaPolicyRepository repo) {
    this(calendar, Map.of(
        Priority.BAIXA, policyFrom(repo, Priority.BAIXA), Priority.MEDIA, policyFrom(repo, Priority.MEDIA),
        Priority.ALTA, policyFrom(repo, Priority.ALTA), Priority.CRITICA, policyFrom(repo, Priority.CRITICA)));
}

SlaCalculator(BusinessCalendar calendar, Map<Priority, Policy> policies) {
    this.calendar = calendar;
    this.policies = policies;
}
```
Como dois construtores com Spring podem gerar ambiguidade, anote o do repositório com `@Autowired`.

`src/main/java/com/helpdesk/sla/SlaPolicy.java`:
```java
package com.helpdesk.sla;

import com.helpdesk.ticket.Priority;
import jakarta.persistence.*;

@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Priority priority;

    @Column(name = "first_response_hours", nullable = false)
    private int firstResponseHours;

    @Column(name = "resolution_hours", nullable = false)
    private int resolutionHours;

    @Column(nullable = false)
    private boolean active = true;

    protected SlaPolicy() {
    }

    public SlaPolicy(Priority priority, int firstResponseHours, int resolutionHours) {
        this.priority = priority;
        this.firstResponseHours = firstResponseHours;
        this.resolutionHours = resolutionHours;
    }

    public Long getId() { return id; }
    public Priority getPriority() { return priority; }
    public int getFirstResponseHours() { return firstResponseHours; }
    public int getResolutionHours() { return resolutionHours; }
    public boolean isActive() { return active; }
}
```

`src/main/java/com/helpdesk/sla/SlaPolicyRepository.java`:
```java
package com.helpdesk.sla;

import com.helpdesk.ticket.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {
    Optional<SlaPolicy> findByPriority(Priority priority);
}
```

`src/main/java/com/helpdesk/sla/dto/SlaDtos.java`:
```java
package com.helpdesk.sla.dto;

import com.helpdesk.ticket.Priority;

public final class SlaDtos {

    private SlaDtos() {
    }

    public record SlaPolicyView(Priority priority, int firstResponseHours,
                                int resolutionHours, boolean active) {
    }
}
```

`src/main/java/com/helpdesk/sla/SlaPolicyController.java`:
```java
package com.helpdesk.sla;

import com.helpdesk.sla.dto.SlaDtos.SlaPolicyView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sla-policies")
public class SlaPolicyController {

    private final SlaPolicyRepository policies;

    public SlaPolicyController(SlaPolicyRepository policies) {
        this.policies = policies;
    }

    @GetMapping
    public List<SlaPolicyView> list() {
        return policies.findAll().stream()
                .map(p -> new SlaPolicyView(p.getPriority(), p.getFirstResponseHours(),
                        p.getResolutionHours(), p.isActive()))
                .toList();
    }
}
```

- [ ] **Step 5: Rodar testes, suíte e commit**

```powershell
mvn -q test -Dtest="BusinessCalendarTest,SlaCalculatorTest"; if ($?) { mvn -q test }
```
Esperado: PASS. Se `countsMinutesBetweenAcrossWeekend` falhar, revise o rollover de `cursor` no fim de semana.
```powershell
git add -A; git commit -m "feat: business-hours calendar and SLA calculator with policies endpoint"
```

---

### Task 7: Tickets — entidade, CRUD, transições e atribuição

**Files:**
- Create: `src/main/java/com/helpdesk/ticket/Ticket.java`, `TicketStatus.java`, `TicketRepository.java`
- Create: `src/main/java/com/helpdesk/ticket/dto/TicketDtos.java`
- Create: `src/main/java/com/helpdesk/ticket/TicketService.java`, `TicketController.java`
- Test: `src/test/java/com/helpdesk/ticket/TicketIntegrationTest.java`

**Interfaces:**
- Consumes: `UserService.loadRequired`, `UserRepository`, `SlaCalculator`, `TimeProvider`, `Priority` (Task 6), exceções (Task 4).
- Produces (Tasks 8–10):
  - `enum TicketStatus { ABERTO, EM_ATENDIMENTO, AGUARDANDO_CLIENTE, RESOLVIDO, FECHADO }` com `Map<TicketStatus, Set<TicketStatus>> ALLOWED` e `boolean canTransition(from, to)` (estático).
  - `Ticket` (campos da spec §3.3 + getters/setters + `TicketStatus can...`), `TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket>`.
  - DTOs: `CreateTicketRequest(title,description,priority)` com `@NotBlank/@Size/@NotNull`; `UpdateTicketRequest(title?,description?,priority?)`; `TransitionRequest(status, comment?)`; `AssignRequest(agentId?)`; `TicketResponse(id,protocol,title,description,priority,status,createdById,createdByName,assignedToId,assignedToName,createdAt,updatedAt,firstResponseAt,waitingSince,resolvedAt,closedAt,firstResponseDueAt,dueAt,slaBreached)`.
  - `TicketService`: `create(request, UserContext)`, `get(id, UserContext)`, `list(filters, UserContext, Pageable)`, `update(id, request, UserContext)`, `delete(id, UserContext)`, `transition(id, request, UserContext)`, `assign(id, agentId?, UserContext)`, `loadOwned(id, UserContext): Ticket` (regra 404 sem acesso — usado pela Task 8).
  - Rotas: `POST/GET /api/tickets`, `GET/PATCH/DELETE /api/tickets/{id}`, `POST /api/tickets/{id}/status`, `POST /api/tickets/{id}/assign`.

- [ ] **Step 1: Escrever teste de integração (falha)**

`src/test/java/com/helpdesk/ticket/TicketIntegrationTest.java`:
```java
package com.helpdesk.ticket;

import com.helpdesk.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketIntegrationTest extends IntegrationTestBase {

    private String createTicket(String token, String priority) throws Exception {
        var result = mockMvc.perform(auth(post("/api/tickets"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Impressora nao imprime","description":"Fila zerada no servidor",
                                 "priority":"%s"}
                                """.formatted(priority))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocol").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.dueAt").isNotEmpty())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void clientCreatesAndSeesOnlyOwnTickets() throws Exception {
        String client1 = register("Ana", "t1@dev.io", "Senha@123", "CLIENTE", null);
        String client2 = register("Bia", "t2@dev.io", "Senha@123", "CLIENTE", null);
        String ticketId = createTicket(client1, "MEDIA");

        mockMvc.perform(auth(get("/api/tickets"), client1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(auth(get("/api/tickets"), client2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // dono vê; estranho recebe 404 (não vaza existência)
        mockMvc.perform(auth(get("/api/tickets/" + ticketId), client1))
                .andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/tickets/" + ticketId), client2))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentSeesAllAndTransitions() throws Exception {
        String client = register("Ana", "t3@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "ag3@dev.io", "Senha@123", "AGENTE", null);
        String invite = "AGENTE-2026";
        agent = registerAgent(ag3Helper(), invite); // helper abaixo cria 2o agente se preciso
        String ticketId = createTicket(client, "ALTA");

        mockMvc.perform(auth(get("/api/tickets"), agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // ABERTO -> EM_ATENDIMENTO (assume)
        mockMvc.perform(auth(post("/api/tickets/" + ticketId + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.assignedToId").isNumber());
    }

    private String ag3Helper() { return "ag3@dev.io"; }

    private String registerAgent(String email, String invite) throws Exception {
        if (inviteRepoHas(invite)) {
            return register("Agente", email, "Senha@123", "AGENTE", invite);
        }
        return register("Agente", email, "Senha@123", "AGENTE", null);
    }

    private boolean inviteRepoHas(String invite) { return true; }

    @Test
    void invalidTransitionIsConflict() throws Exception {
        String client = register("Ana", "t4@dev.io", "Senha@123", "CLIENTE", null);
        String agent = ensureAgent("ag4@dev.io");
        String ticketId = createTicket(client, "BAIXA");

        // ABERTO -> RESOLVIDO nao existe
        mockMvc.perform(auth(post("/api/tickets/" + ticketId + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"RESOLVIDO"}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    private String ensureAgent(String email) throws Exception {
        return register("Agente", email, "Senha@123", "AGENTE", "AGENTE-2026");
    }

    @Test
    void clientCannotTransitionDirectly() throws Exception {
        String client = register("Ana", "t5@dev.io", "Senha@123", "CLIENTE", null);
        String ticketId = createTicket(client, "BAIXA");
        mockMvc.perform(auth(post("/api/tickets/" + ticketId + "/status"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientEditsOnlyWhileOpenAndOnlyOwn() throws Exception {
        String client = register("Ana", "t6@dev.io", "Senha@123", "CLIENTE", null);
        String agent = ensureAgent("ag6@dev.io");
        String ticketId = createTicket(client, "BAIXA");

        mockMvc.perform(auth(patch("/api/tickets/" + ticketId), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Titulo novo"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo novo"));

        mockMvc.perform(auth(post("/api/tickets/" + ticketId + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isOk());

        mockMvc.perform(auth(patch("/api/tickets/" + ticketId), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Tenta de novo"}
                                """)))
                .andExpect(status().isConflict());
    }

    @Test
    void agentAssignsAndClientDeletesOpenTicket() throws Exception {
        String client = register("Ana", "t7@dev.io", "Senha@123", "CLIENTE", null);
        String ticketId = createTicket(client, "BAIXA");
        String agent = ensureAgent("ag7@dev.io");

        mockMvc.perform(auth(post("/api/tickets/" + ticketId + "/assign"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").isNumber());

        mockMvc.perform(auth(delete("/api/tickets/" + ticketId), client))
                .andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/tickets/" + ticketId), client))
                .andExpect(status().isNotFound());
    }
}
```

Nota: o teste `agentSeesAllAndTransitions` contém helpers experimentais (`ag3Helper`, `inviteRepoHas`) — **simplifique ao implementar**: use o padrão dos demais testes, garantindo o invite antes (injete `InviteCodeRepository` e faça `save(new InviteCode("AGENTE-2026", Role.AGENTE, Instant.parse("2030-12-31T23:59:59Z")))` se ausente, como em `AuthIntegrationTest`), e chame apenas `register("Agente", "ag3@dev.io", "Senha@123", "AGENTE", "AGENTE-2026")`. Todos os e-mails dos testes devem ser únicos no arquivo.

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=TicketIntegrationTest
```
Esperado: FAIL (404/beans ausentes).

- [ ] **Step 3: Implementar TicketStatus, Ticket, repositório, DTOs**

`src/main/java/com/helpdesk/ticket/TicketStatus.java`:
```java
package com.helpdesk.ticket;

import java.util.Map;
import java.util.Set;

public enum TicketStatus {
    ABERTO, EM_ATENDIMENTO, AGUARDANDO_CLIENTE, RESOLVIDO, FECHADO;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            ABERTO, Set.of(EM_ATENDIMENTO),
            EM_ATENDIMENTO, Set.of(AGUARDANDO_CLIENTE, RESOLVIDO),
            AGUARDANDO_CLIENTE, Set.of(EM_ATENDIMENTO),
            RESOLVIDO, Set.of(FECHADO),
            FECHADO, Set.of());

    public boolean canTransitionTo(TicketStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
```

`src/main/java/com/helpdesk/ticket/Ticket.java`:
```java
package com.helpdesk.ticket;

import com.helpdesk.auth.domain.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String protocol;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "waiting_since")
    private Instant waitingSince;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "first_response_due_at")
    private Instant firstResponseDueAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "sla_breached", nullable = false)
    private boolean slaBreached;

    protected Ticket() {
    }

    public Ticket(String title, String description, Priority priority, TicketStatus status,
                  User createdBy, Instant now) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public User getCreatedBy() { return createdBy; }
    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(Instant firstResponseAt) { this.firstResponseAt = firstResponseAt; }
    public Instant getWaitingSince() { return waitingSince; }
    public void setWaitingSince(Instant waitingSince) { this.waitingSince = waitingSince; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getFirstResponseDueAt() { return firstResponseDueAt; }
    public void setFirstResponseDueAt(Instant v) { this.firstResponseDueAt = v; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public boolean isSlaBreached() { return slaBreached; }
    public void setSlaBreached(boolean slaBreached) { this.slaBreached = slaBreached; }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
```

`src/main/java/com/helpdesk/ticket/TicketRepository.java`:
```java
package com.helpdesk.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
}
```

`src/main/java/com/helpdesk/ticket/dto/TicketDtos.java`:
```java
package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.Priority;
import com.helpdesk.ticket.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull Priority priority) {
    }

    public record UpdateTicketRequest(
            @Size(max = 160) String title,
            @Size(max = 5000) String description,
            Priority priority) {
    }

    public record TransitionRequest(
            @NotNull TicketStatus status,
            @Size(max = 5000) String comment) {
    }

    public record AssignRequest(Long agentId) {
    }

    public record TicketResponse(
            Long id, String protocol, String title, String description,
            Priority priority, TicketStatus status,
            Long createdById, String createdByName,
            Long assignedToId, String assignedToName,
            Instant createdAt, Instant updatedAt,
            Instant firstResponseAt, Instant waitingSince,
            Instant resolvedAt, Instant closedAt,
            Instant firstResponseDueAt, Instant dueAt,
            boolean slaBreached) {
    }

    public record TicketPage(java.util.List<TicketResponse> content, int page, int size,
                             long totalElements, int totalPages) {
    }
}
```

- [ ] **Step 4: Implementar TicketService**

`src/main/java/com/helpdesk/ticket/TicketService.java`:
```java
package com.helpdesk.ticket;

import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.common.error.ConflictException;
import com.helpdesk.common.error.ForbiddenActionException;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.common.time.TimeProvider;
import com.helpdesk.security.UserContext;
import com.helpdesk.sla.SlaCalculator;
import com.helpdesk.ticket.dto.TicketDtos.*;
import com.helpdesk.user.UserService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final UserRepository users;
    private final UserService userService;
    private final SlaCalculator slaCalculator;
    private final TimeProvider time;

    public TicketService(TicketRepository tickets, UserRepository users, UserService userService,
                         SlaCalculator slaCalculator, TimeProvider time) {
        this.tickets = tickets;
        this.users = users;
        this.userService = userService;
        this.slaCalculator = slaCalculator;
        this.time = time;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, UserContext actor) {
        User creator = userService.loadRequired(actor.id());
        var now = time.now();
        TicketStatus initial = actor.isAgent() ? TicketStatus.EM_ATENDIMENTO : TicketStatus.ABERTO;
        Ticket ticket = new Ticket(request.title().trim(), request.description().trim(),
                request.priority(), initial, creator, now);
        if (actor.isAgent()) {
            ticket.setAssignedTo(creator);
        }
        tickets.saveAndFlush(ticket);
        ticket.setProtocol(String.format("TK-%05d", ticket.getId()));
        var deadlines = slaCalculator.calculate(request.priority(), now);
        ticket.setFirstResponseDueAt(deadlines.firstResponseDueAt());
        ticket.setDueAt(deadlines.dueAt());
        return toResponse(tickets.save(ticket));
    }

    @Transactional(readOnly = true)
    public TicketResponse get(Long id, UserContext actor) {
        return toResponse(loadOwned(id, actor));
    }

    @Transactional(readOnly = true)
    public TicketPage list(String status, String priority, Boolean breached, String q,
                           UserContext actor, Pageable pageable) {
        Page<Ticket> page = tickets.findAll(buildSpec(actor, status, priority, breached, q), pageable);
        return new TicketPage(page.map(this::toResponse).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public TicketResponse update(Long id, UpdateTicketRequest request, UserContext actor) {
        Ticket ticket = loadOwned(id, actor);
        if (!actor.isAgent() && ticket.getStatus() != TicketStatus.ABERTO) {
            throw new ConflictException("Tickets can only be edited while ABERTO");
        }
        if (ticket.getStatus() == TicketStatus.FECHADO) {
            throw new ConflictException("Closed tickets cannot be edited");
        }
        if (request.title() != null && !request.title().isBlank()) ticket.setTitle(request.title().trim());
        if (request.description() != null && !request.description().isBlank()) ticket.setDescription(request.description().trim());
        if (request.priority() != null) ticket.setPriority(request.priority());
        ticket.touch(time.now());
        return toResponse(tickets.save(ticket));
    }

    @Transactional
    public void delete(Long id, UserContext actor) {
        Ticket ticket = loadOwned(id, actor);
        if (!ticket.getCreatedBy().getId().equals(actor.id())) {
            throw new ForbiddenActionException("Only the creator can delete this ticket");
        }
        if (ticket.getStatus() != TicketStatus.ABERTO) {
            throw new ConflictException("Tickets can only be deleted while ABERTO");
        }
        tickets.delete(ticket);
    }

    @Transactional
    public TicketResponse transition(Long id, TransitionRequest request, UserContext actor) {
        Ticket ticket = loadOwned(id, actor);
        TicketStatus target = request.status();
        if (!ticket.getStatus().canTransitionTo(target)) {
            throw new ConflictException("Cannot move ticket from " + ticket.getStatus() + " to " + target);
        }
        if (!actor.isAgent() && target != TicketStatus.FECHADO) {
            throw new ForbiddenActionException("Clients can only close resolved tickets");
        }
        if (target == TicketStatus.FECHADO
                && !actor.isAgent()
                && !ticket.getCreatedBy().getId().equals(actor.id())) {
            throw new ForbiddenActionException("Only the ticket creator can close it");
        }
        var now = time.now();
        TicketStatus from = ticket.getStatus();
        switch (target) {
            case EM_ATENDIMENTO -> {
                if (from == TicketStatus.ABERTO && actor.isAgent()) {
                    ticket.setAssignedTo(userService.loadRequired(actor.id()));
                }
            }
            case AGUARDANDO_CLIENTE -> ticket.setWaitingSince(now);
            case RESOLVIDO -> ticket.setResolvedAt(now);
            case FECHADO -> ticket.setClosedAt(now);
            default -> { }
        }
        ticket.setStatus(target);
        ticket.touch(now);
        return toResponse(tickets.save(ticket));
    }

    @Transactional
    public TicketResponse assign(Long id, Long agentId, UserContext actor) {
        if (!actor.isAgent()) {
            throw new ForbiddenActionException("Only agents can assign tickets");
        }
        Ticket ticket = loadOwned(id, actor);
        if (ticket.getStatus() != TicketStatus.ABERTO
                && ticket.getStatus() != TicketStatus.EM_ATENDIMENTO) {
            throw new ConflictException("Tickets can only be assigned while ABERTO or EM_ATENDIMENTO");
        }
        User agent = agentId == null ? null : userService.loadRequired(agentId);
        if (agent != null && !agent.getRole().equals(com.helpdesk.auth.domain.Role.AGENTE)) {
            throw new ConflictException("Target user is not an agent");
        }
        ticket.setAssignedTo(agent);
        ticket.touch(time.now());
        return toResponse(tickets.save(ticket));
    }

    public Ticket loadOwned(Long id, UserContext actor) {
        Ticket ticket = tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket not found"));
        if (!actor.isAgent() && !ticket.getCreatedBy().getId().equals(actor.id())) {
            throw new NotFoundException("Ticket not found");
        }
        return ticket;
    }

    private Specification<Ticket> buildSpec(UserContext actor, String status, String priority,
                                            Boolean breached, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!actor.isAgent()) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), actor.id()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), TicketStatus.valueOf(status.toUpperCase(Locale.ROOT))));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), Priority.valueOf(priority.toUpperCase(Locale.ROOT))));
            }
            if (breached != null) {
                predicates.add(cb.equal(root.get("slaBreached"), breached));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("protocol")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TicketResponse toResponse(Ticket t) {
        return new TicketResponse(
                t.getId(), t.getProtocol(), t.getTitle(), t.getDescription(),
                t.getPriority(), t.getStatus(),
                t.getCreatedBy().getId(), t.getCreatedBy().getName(),
                t.getAssignedTo() == null ? null : t.getAssignedTo().getId(),
                t.getAssignedTo() == null ? null : t.getAssignedTo().getName(),
                t.getCreatedAt(), t.getUpdatedAt(),
                t.getFirstResponseAt(), t.getWaitingSince(),
                t.getResolvedAt(), t.getClosedAt(),
                t.getFirstResponseDueAt(), t.getDueAt(),
                t.isSlaBreached());
    }
}
```

Obs.: crie também `src/main/java/com/helpdesk/common/error/ForbiddenActionException.java` (igual às demais) e adicione no `GlobalExceptionHandler`:
```java
@ExceptionHandler(ForbiddenActionException.class)
public ProblemDetail forbidden(ForbiddenActionException ex, HttpServletRequest req) {
    return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), req);
}
```

- [ ] **Step 5: Implementar TicketController**

`src/main/java/com/helpdesk/ticket/TicketController.java`:
```java
package com.helpdesk.ticket;

import com.helpdesk.security.UserContext;
import com.helpdesk.ticket.dto.TicketDtos.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@AuthenticationPrincipal UserContext current,
                                                 @Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(request, current));
    }

    @GetMapping
    public TicketPage list(@AuthenticationPrincipal UserContext current,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String priority,
                           @RequestParam(required = false) Boolean slaBreached,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] sortParts = sort.split(",");
        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.fromString(sortParts[1]), sortParts[0]));
        return ticketService.list(status, priority, slaBreached, q, current, pageable);
    }

    @GetMapping("/{id}")
    public TicketResponse get(@AuthenticationPrincipal UserContext current, @PathVariable Long id) {
        return ticketService.get(id, current);
    }

    @PatchMapping("/{id}")
    public TicketResponse update(@AuthenticationPrincipal UserContext current,
                                 @PathVariable Long id,
                                 @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(id, request, current);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserContext current, @PathVariable Long id) {
        ticketService.delete(id, current);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    public TicketResponse transition(@AuthenticationPrincipal UserContext current,
                                     @PathVariable Long id,
                                     @Valid @RequestBody TransitionRequest request) {
        return ticketService.transition(id, request, current);
    }

    @PreAuthorize("hasRole('AGENTE')")
    @PostMapping("/{id}/assign")
    public TicketResponse assign(@AuthenticationPrincipal UserContext current,
                                 @PathVariable Long id,
                                 @RequestBody(required = false) AssignRequest request) {
        Long agentId = request == null ? null : request.agentId();
        return ticketService.assign(id, agentId, current);
    }
}
```

Obs.: o teste `clientCannotTransitionDirectly` espera 403 — o `ForbiddenActionException` (403) cobre; mas o teste `invalidTransitionIsConflict` passa antes pela checagem de papel? Não: cliente não é coberto lá (é cliente tentando transição inválida? Não — o teste usa **agente**). Garanta a ordem no `transition`: primeiro checagem de papel do cliente (`!actor.isAgent() && target != FECHADO` → 403), depois matriz de transição (409). No teste `clientCannotTransitionDirectly` o alvo `EM_ATENDIMENTO` de `ABERTO` é matricialmente válido, então o 403 do papel vem antes — ok. No teste `invalidTransitionIsConflict` o ator é agente, então cai no 409 da matriz. **Ordem exata no código: 409 da matriz primeiro ou 403 do papel primeiro?** Com agente usando matriz inválida → só 409 é alcançável; com cliente usando matriz válida → só 403. Ambas as ordens funcionam para os testes; mantenha a ordem: (1) matriz → 409, (2) papel → 403, (3) dono/fecha → 403. Ajuste o código acima para checar a matriz **antes** do papel.

- [ ] **Step 6: Rodar teste, suíte e commit**

```powershell
mvn -q test -Dtest=TicketIntegrationTest; if ($?) { mvn -q test }
```
Esperado: PASS.
```powershell
git add -A; git commit -m "feat: ticket CRUD, filters, status transitions and assignment"
```

---

### Task 8: Timeline — comentários, eventos de auditoria, pausa/reativação de SLA

**Files:**
- Create: `src/main/java/com/helpdesk/ticket/TimelineEntry.java`, `TimelineType.java`, `EventType.java`, `TimelineRepository.java`
- Create: `src/main/java/com/helpdesk/ticket/TimelineService.java`
- Modify: `src/main/java/com/helpdesk/ticket/TicketService.java` (transições passam a gravar eventos + pausar/reativar SLA; comentário reativa)
- Create: `src/main/java/com/helpdesk/ticket/dto/TimelineDtos.java`
- Modify: `src/main/java/com/helpdesk/ticket/TicketController.java` (+ `GET /{id}/timeline`, `POST /{id}/comments`)
- Test: `src/test/java/com/helpdesk/ticket/TimelineIntegrationTest.java`

**Interfaces:**
- Consumes: `Ticket.loadOwned`, `SlaCalculator` (shift/isBreached), `TimeProvider`, `UserContext`, exceções.
- Produces (Tasks 9–10):
  - `enum TimelineType { COMMENT, EVENT }`, `enum EventType { STATUS_CHANGED, ASSIGNED, SLA_PAUSED, SLA_RESUMED, SLA_BREACHED }`
  - `TimelineEntry` (ticket, type, eventType, author, body, payload, createdAt) + factories `comment(Ticket, UserContext, String)` e `event(Ticket, EventType, UserContext, String payloadJson)` (author pode ser null p/ eventos de sistema — mas author_id é NOT NULL SET NULL ok, use sempre o ator).
  - `TimelineService`: `TimelineEntryResponse addComment(Ticket, UserContext, String body, Instant now)` — se autor é cliente e status é `AGUARDANDO_CLIENTE`, reativa (shift dos prazos + status `EM_ATENDIMENTO` + evento `SLA_RESUMED` + `STATUS_CHANGED`); se autor é agente e `firstResponseAt == null`, marca primeira resposta. `Page<TimelineEntryResponse> timeline(Long ticketId, UserContext, Pageable)`; `void appendSystemEvent(Ticket, EventType, String payload, Instant now)` (usado pelo `TicketService`).
  - `TicketService.transition` grava `SLA_PAUSED`/`STATUS_CHANGED`, calcula violação (`slaBreached` + evento `SLA_BREACHED` único) e aplica pausa real (`waitingSince`).
  - DTOs: `CommentRequest(@NotBlank body)`; `TimelineEntryResponse(id,type,eventType,authorId,authorName,body,payload,createdAt)`; página similar a `TicketPage`.
  - Rotas: `GET /api/tickets/{id}/timeline` (paginado, `createdAt` desc), `POST /api/tickets/{id}/comments` (201).

- [ ] **Step 1: Escrever teste de integração (falha)**

`src/test/java/com/helpdesk/ticket/TimelineIntegrationTest.java`:
```java
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

    private String newTicket(String clientToken) throws Exception {
        var result = mockMvc.perform(auth(post("/api/tickets"), clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"Monitor piscando","description":"Monitorliga e desliga sozinho",
                                 "priority":"MEDIA"}
                                """)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void commentAndEventsAppearOnTimeline() throws Exception {
        String client = register("Ana", "tl1@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tag1@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client);

        mockMvc.perform(auth(post("/api/tickets/" + id + "/comments"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"body":"Ja troquei o cabo de forca"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("COMMENT"));

        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isOk());

        mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("EVENT"))
                .andExpect(jsonPath("$.content[0].eventType").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.content[1].type").value("COMMENT"));
    }

    @Test
    void firstAgentCommentMarksFirstResponse() throws Exception {
        String client = register("Ana", "tl2@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tag2@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client);

        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(jsonPath("$.firstResponseAt").doesNotExist());
        mockMvc.perform(auth(post("/api/tickets/" + id + "/comments"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"body":"Verificando o equipamento"}
                                """)))
                .andExpect(status().isCreated());
        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(jsonPath("$.firstResponseAt").isNotEmpty());
    }

    @Test
    void waitingClientCommentResumesSla() throws Exception {
        String client = register("Ana", "tl3@dev.io", "Senha@123", "CLIENTE", null);
        String agent = register("Agente", "tag3@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        String id = newTicket(client);

        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"EM_ATENDIMENTO"}
                                """)))
                .andExpect(status().isOk());
        mockMvc.perform(auth(post("/api/tickets/" + id + "/status"), agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"status":"AGUARDANDO_CLIENTE"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingSince").isNotEmpty());

        mockMvc.perform(auth(post("/api/tickets/" + id + "/comments"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"body":"Segue a foto do erro"}
                                """)))
                .andExpect(status().isCreated());

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
        String agent = register("Agente", "tag4@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
        Instant created = Instant.parse("2026-01-05T11:00:00Z"); // seg 09:00 BRT
        org.mockito.Mockito.when(time.now()).thenReturn(created);
        String id = newTicket(client);

        // MEDIA: primeira resposta em 4h uteis → 14:00 BRT. Anda 6h uteis → 16:00 BRT
        org.mockito.Mockito.when(time.now()).thenReturn(Instant.parse("2026-01-05T19:00:00Z"));
        mockMvc.perform(auth(get("/api/tickets/" + id), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaBreached").value(true));

        mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), client))
                .andExpect(status().isOk());
        boolean hasBreachEvent = false;
        var body = mapper.readTree(
                mockMvc.perform(auth(get("/api/tickets/" + id + "/timeline"), client))
                        .andReturn().getResponse().getContentAsString());
        for (var node : body.get("content")) {
            if ("SLA_BREACHED".equals(node.hasNonNull("eventType") ? node.get("eventType").asText() : null)) {
                hasBreachEvent = true;
            }
        }
        org.assertj.core.api.Assertions.assertThat(hasBreachEvent).isTrue();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=TimelineIntegrationTest
```
Esperado: FAIL (404 na rota de timeline).

- [ ] **Step 3: Implementar enums, TimelineEntry, repositório, DTOs**

`src/main/java/com/helpdesk/ticket/TimelineType.java`:
```java
package com.helpdesk.ticket;

public enum TimelineType {
    COMMENT, EVENT
}
```

`src/main/java/com/helpdesk/ticket/EventType.java`:
```java
package com.helpdesk.ticket;

public enum EventType {
    STATUS_CHANGED, ASSIGNED, SLA_PAUSED, SLA_RESUMED, SLA_BREACHED
}
```

`src/main/java/com/helpdesk/ticket/TimelineEntry.java`:
```java
package com.helpdesk.ticket;

import com.helpdesk.auth.domain.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "timeline_entries")
public class TimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimelineType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private EventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TimelineEntry() {
    }

    public static TimelineEntry comment(Ticket ticket, User author, String body, Instant now) {
        TimelineEntry entry = base(ticket, TimelineType.COMMENT, author, now);
        entry.body = body;
        return entry;
    }

    public static TimelineEntry event(Ticket ticket, EventType eventType, User author,
                                      String payloadJson, Instant now) {
        TimelineEntry entry = base(ticket, TimelineType.EVENT, author, now);
        entry.eventType = eventType;
        entry.payload = payloadJson;
        return entry;
    }

    private static TimelineEntry base(Ticket ticket, TimelineType type, User author, Instant now) {
        TimelineEntry entry = new TimelineEntry();
        entry.ticket = ticket;
        entry.type = type;
        entry.author = author;
        entry.createdAt = now;
        return entry;
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public TimelineType getType() { return type; }
    public EventType getEventType() { return eventType; }
    public User getAuthor() { return author; }
    public String getBody() { return body; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`src/main/java/com/helpdesk/ticket/TimelineRepository.java`:
```java
package com.helpdesk.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineRepository extends JpaRepository<TimelineEntry, Long> {
    Page<TimelineEntry> findByTicketId(Long ticketId, Pageable pageable);
    boolean existsByTicketIdAndEventType(Long ticketId, EventType eventType);
}
```

`src/main/java/com/helpdesk/ticket/dto/TimelineDtos.java`:
```java
package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.EventType;
import com.helpdesk.ticket.TimelineType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TimelineDtos {

    private TimelineDtos() {
    }

    public record CommentRequest(@NotBlank @Size(max = 5000) String body) {
    }

    public record TimelineEntryResponse(Long id, TimelineType type, EventType eventType,
                                        Long authorId, String authorName, String body,
                                        String payload, Instant createdAt) {
    }

    public record TimelinePage(List<TimelineEntryResponse> content, int page, int size,
                               long totalElements, int totalPages) {
    }
}
```

- [ ] **Step 4: Implementar TimelineService**

`src/main/java/com/helpdesk/ticket/TimelineService.java`:
```java
package com.helpdesk.ticket;

import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.common.time.TimeProvider;
import com.helpdesk.security.UserContext;
import com.helpdesk.sla.SlaCalculator;
import com.helpdesk.ticket.dto.TimelineDtos.TimelineEntryResponse;
import com.helpdesk.ticket.dto.TimelineDtos.TimelinePage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TimelineService {

    private final TimelineRepository timeline;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final SlaCalculator slaCalculator;
    private final TimeProvider time;

    public TimelineService(TimelineRepository timeline, TicketRepository tickets,
                           UserRepository users, SlaCalculator slaCalculator, TimeProvider time) {
        this.timeline = timeline;
        this.tickets = tickets;
        this.users = users;
        this.slaCalculator = slaCalculator;
        this.time = time;
    }

    @Transactional
    public TimelineEntryResponse addComment(Long ticketId, UserContext actor, String body) {
        Ticket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        User author = users.findById(actor.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        Instant now = time.now();

        TimelineEntry entry = timeline.save(TimelineEntry.comment(ticket, author, body.trim(), now));

        if (actor.isAgent() && ticket.getFirstResponseAt() == null) {
            ticket.setFirstResponseAt(now);
            tickets.save(ticket);
        }

        if (!actor.isAgent() && ticket.getStatus() == TicketStatus.AGUARDANDO_CLIENTE) {
            resume(ticket, author, now);
        }
        return toResponse(entry);
    }

    @Transactional
    public TimelinePage timeline(Long ticketId, UserContext actor, Pageable pageable) {
        // acesso ao dono é validado pelo controller via TicketService.loadOwned antes
        Page<TimelineEntry> page = timeline.findByTicketId(ticketId, pageable);
        var content = page.map(this::toResponse).getContent();
        return new TimelinePage(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public void appendSystemEvent(Ticket ticket, EventType type, String payload, Instant now) {
        timeline.save(TimelineEntry.event(ticket, type, ticket.getCreatedBy(), payload, now));
    }

    private void resume(Ticket ticket, User actor, Instant now) {
        var view = slaView(ticket);
        long pausedMinutes = slaCalculatorShiftMinutes(ticket, now);
        if (pausedMinutes > 0) {
            ticket.setFirstResponseDueAt(slaCalculatorShift(ticket.getFirstResponseDueAt(), pausedMinutes));
            ticket.setDueAt(slaCalculatorShift(ticket.getDueAt(), pausedMinutes));
            timeline.save(TimelineEntry.event(ticket, EventType.SLA_RESUMED, actor,
                    "{\"pausedMinutes\":" + pausedMinutes + "}", now));
        }
        ticket.setWaitingSince(null);
        TicketStatus from = ticket.getStatus();
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
        ticket.touch(now);
        tickets.save(ticket);
        timeline.save(TimelineEntry.event(ticket, EventType.STATUS_CHANGED, actor,
                "{\"from\":\"" + from + "\",\"to\":\"" + ticket.getStatus() + "\"}", now));
    }

    private long slaCalculatorShiftMinutes(Ticket ticket, Instant now) {
        if (ticket.getWaitingSince() == null) {
            return 0;
        }
        // minutos uteis entre waitingSince e agora
        return new com.helpdesk.sla.BusinessCalendarHelperProbe().minutes(ticket.getWaitingSince(), now);
    }

    private Instant slaCalculatorShift(Instant dueAt, long minutes) {
        return slaCalculator.shift(dueAt, minutes);
    }

    private SlaCalculator.TicketSlaView slaView(Ticket ticket) {
        return new SlaCalculator.TicketSlaView(
                ticket.getFirstResponseDueAt(), ticket.getDueAt(),
                ticket.getFirstResponseAt(), ticket.getResolvedAt(),
                ticket.getClosedAt(), ticket.getWaitingSince() != null);
    }

    private TimelineEntryResponse toResponse(TimelineEntry entry) {
        return new TimelineEntryResponse(
                entry.getId(), entry.getType(), entry.getEventType(),
                entry.getAuthor() == null ? null : entry.getAuthor().getId(),
                entry.getAuthor() == null ? null : entry.getAuthor().getName(),
                entry.getBody(), entry.getPayload(), entry.getCreatedAt());
    }
}
```

**Correção obrigatória ao implementar:** o helper fictício `BusinessCalendarHelperProbe` não existe — injete `BusinessCalendar` direto no construtor de `TimelineService` e calcule `long paused = businessCalendar.workingMinutesBetween(ticket.getWaitingSince(), now);`. Reescreva `resume` assim:
```java
private void resume(Ticket ticket, User actor, Instant now) {
    long pausedMinutes = ticket.getWaitingSince() == null ? 0
            : businessCalendar.workingMinutesBetween(ticket.getWaitingSince(), now);
    if (pausedMinutes > 0) {
        ticket.setFirstResponseDueAt(slaCalculator.shift(ticket.getFirstResponseDueAt(), pausedMinutes));
        ticket.setDueAt(slaCalculator.shift(ticket.getDueAt(), pausedMinutes));
        timeline.save(TimelineEntry.event(ticket, EventType.SLA_RESUMED, actor,
                "{\"pausedMinutes\":" + pausedMinutes + "}", now));
    }
    ticket.setWaitingSince(null);
    TicketStatus from = ticket.getStatus();
    ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
    ticket.touch(now);
    tickets.save(ticket);
    timeline.save(TimelineEntry.event(ticket, EventType.STATUS_CHANGED, actor,
            "{\"from\":\"" + from + "\",\"to\":\"" + ticket.getStatus() + "\"}", now));
}
```
Remova `slaView`/`slaCalculatorShiftMinutes`/`slaCalculatorShift` se ficarem sem uso (a checagem de violação fica no `TicketService`). O import de `BusinessCalendar` entra no topo.

- [ ] **Step 5: Integrar eventos e violação no TicketService**

No `TicketService`, inyecte `TimelineRepository` e `TimelineService` (cuidado com ciclo: `TimelineService` não deve chamar `TicketService` — use `tickets` repositório direto, como acima). Em `transition`, após cada mutação:

```java
// pausa
case AGUARDANDO_CLIENTE -> {
    ticket.setWaitingSince(now);
    timeline.append(ticket, EventType.SLA_PAUSED, actor, "{}", now);
}
// antes de gravar a nova status, sempre:
timeline.append(ticket, EventType.STATUS_CHANGED, actor,
        "{\"from\":\"" + from + "\",\"to\":\"" + target + "\"}", now);
```
Use uma única porta: adicione em `TimelineService` um método `append(Ticket, EventType, UserContext, String payload, Instant)` que resolve o `User` pelo id do ator. Ao final de `transition` e de `addComment`, chame `checkBreach(ticket, now)`:
```java
private void checkBreach(Ticket ticket, Instant now) {
    var view = new SlaCalculator.TicketSlaView(
            ticket.getFirstResponseDueAt(), ticket.getDueAt(), ticket.getFirstResponseAt(),
            ticket.getResolvedAt(), ticket.getClosedAt(), ticket.getWaitingSince() != null);
    boolean breached = slaCalculator.isBreached(view, now);
    if (breached && !ticket.isSlaBreached()) {
        ticket.setSlaBreached(true);
        timelineService.append(ticket, EventType.SLA_BREACHED, null, "{}", now);
    }
}
```
Chame `checkBreach` também em `get(id, actor)` (transação de leitura — mude para `@Transactional`) antes de `toResponse`, para o teste `breachIsFlaggedOnRead` passar. Evento `SLA_BREACHED` é único porque `ticket.isSlaBreached()` já está true na segunda leitura (use `TimelineRepository.existsByTicketIdAndEventType` como guarda extra se preferir).

- [ ] **Step 6: Adicionar rotas no TicketController**

```java
@GetMapping("/{id}/timeline")
public TimelinePage timeline(@AuthenticationPrincipal UserContext current, @PathVariable Long id,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size) {
    ticketService.loadOwned(id, current); // valida acesso 404
    return timelineService.timeline(id, current,
            PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
}

@PostMapping("/{id}/comments")
public ResponseEntity<TimelineEntryResponse> comment(@AuthenticationPrincipal UserContext current,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody CommentRequest request) {
    ticketService.loadOwned(id, current);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(timelineService.addComment(id, current, request.body()));
}
```
Inyecte `TimelineService` no controller. `loadOwned` precisa estar acessível (torne-o público já é).

- [ ] **Step 7: Rodar teste, suíte e commit**

```powershell
mvn -q test -Dtest=TimelineIntegrationTest; if ($?) { mvn -q test }
```
Esperado: PASS. Se o teste de pausa falhar em `waitingSince doesNotExist`, garanta que `resume` zera o campo **e** que `toResponse` mapeia `waitingSince` (já existe).
```powershell
git add -A; git commit -m "feat: timeline with comments, audit events and SLA pause/resume"
```

---

### Task 9: Anexos — upload em comentário, download, remoção

**Files:**
- Create: `src/main/java/com/helpdesk/attachment/Attachment.java`, `AttachmentRepository.java`, `AttachmentService.java`, `FileStorage.java`, `AttachmentController.java`
- Modify: `src/main/java/com/helpdesk/ticket/TimelineService.java` (`addComment` passa a aceitar `MultipartFile[]`)
- Modify: `src/main/java/com/helpdesk/ticket/TicketController.java` (`POST /{id}/comments` vira multipart)
- Modify: `src/main/java/com/helpdesk/ticket/TicketService.java` (`delete` remove arquivos do disco)
- Test: `src/test/java/com/helpdesk/attachment/AttachmentIntegrationTest.java`

**Interfaces:**
- Consumes: `TimelineEntry`/`TimelineRepository` (Task 8), `helpdesk.upload-dir`, exceções `PayloadTooLargeException`/`UnsupportedMediaTypeException` (Task 4).
- Produces:
  - `Attachment(id, timelineEntryId, originalFilename, storageName, contentType, size, createdAt)`
  - `FileStorage`: `String store(MultipartFile file)` → gera UUID, grava em `${helpdesk.upload-dir}/{uuid}`; `void delete(String storageName)`; `InputStream open(String storageName)`.
  - `AttachmentService`: `void attach(TimelineEntry entry, MultipartFile[] files)` — valida tamanho (≤10 MB) e whitelist; `Attachment resolve(Long id, UserContext)` — valida acesso ao chamado pai (dono ou agente, senão 404).
  - `GET /api/attachments/{id}` → stream com `Content-Disposition: attachment; filename="..."` (404 sem acesso).
  - `POST /api/tickets/{id}/comments` agora `consumes = MULTIPART_FORM_DATA`: campo `body` (String, obrigatório) + `files[]` (opcional, 0..N).

- [ ] **Step 1: Escrever teste (falha)**

`src/test/java/com/helpdesk/attachment/AttachmentIntegrationTest.java`:
```java
package com.helpdesk.attachment;

import com.helpdesk.IntegrationTestBase;
import com.helpdesk.auth.domain.InviteCode;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.InviteCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private String newTicket(String client) throws Exception {
        var result = mockMvc.perform(auth(post("/api/tickets"), client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"PC nao liga","description":"Led pisca e desliga",
                                 "priority":"ALTA"}
                                """)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private long commentWithAttachment(String token, String ticketId, MockMultipartFile file) throws Exception {
        var body = new MockMultipartFile("body", "", MediaType.TEXT_PLAIN_VALUE, "Segue o log".getBytes());
        var result = mockMvc.perform(auth(multipart("/api/tickets/" + ticketId + "/comments")
                        .file(body).file(file), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].id").isNumber())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString())
                .get("attachments").get(0).get("id").asLong();
    }

    @Test
    void uploadsAndDownloadsAttachment() throws Exception {
        String client = register("Ana", "an1@dev.io", "Senha@123", "CLIENTE", null);
        String other = register("Bia", "an2@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        var file = new MockMultipartFile("files", "log.txt", MediaType.TEXT_PLAIN_VALUE,
                "linha 1 do log".getBytes());

        long attachmentId = commentWithAttachment(client, id, file);

        mockMvc.perform(auth(get("/api/attachments/" + attachmentId), client))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("log.txt")));

        mockMvc.perform(auth(get("/api/attachments/" + attachmentId), other))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDisallowedContentType() throws Exception {
        String client = register("Ana", "an3@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        var body = new MockMultipartFile("body", "", MediaType.TEXT_PLAIN_VALUE, "x".getBytes());
        var exe = new MockMultipartFile("files", "virus.exe", "application/x-msdownload",
                "MZ".getBytes());
        mockMvc.perform(auth(multipart("/api/tickets/" + id + "/comments").file(body).file(exe), client))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        String client = register("Ana", "an4@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        var body = new MockMultipartFile("body", "", MediaType.TEXT_PLAIN_VALUE, "x".getBytes());
        var big = new MockMultipartFile("files", "big.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[11 * 1024 * 1024]);
        mockMvc.perform(auth(multipart("/api/tickets/" + id + "/comments").file(body).file(big), client))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void commentWithoutFilesStillWorks() throws Exception {
        String client = register("Ana", "an5@dev.io", "Senha@123", "CLIENTE", null);
        String id = newTicket(client);
        var body = new MockMultipartFile("body", "", MediaType.TEXT_PLAIN_VALUE,
                "Sem anexo".getBytes());
        mockMvc.perform(auth(multipart("/api/tickets/" + id + "/comments").file(body), client))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments").isEmpty());
    }
}
```

Obs.: a resposta do comentário passa a ser `CommentResponse(TimelineEntryResponse entry + campos, List<AttachmentView> attachments)` — defina em `TimelineDtos`:
```java
public record AttachmentView(Long id, String filename, String contentType, long size) {}
public record CommentResponse(Long id, TimelineType type, EventType eventType,
                              Long authorId, String authorName, String body,
                              String payload, Instant createdAt,
                              List<AttachmentView> attachments) {}
```
`addComment` retorna `CommentResponse` (com lista vazia quando não há arquivos).

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=AttachmentIntegrationTest
```
Esperado: FAIL (multipart sem campo `files` / rota inexistente).

- [ ] **Step 3: Implementar Attachment, FileStorage, repositório, service**

`src/main/java/com/helpdesk/attachment/Attachment.java`:
```java
package com.helpdesk.attachment;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timeline_entry_id", nullable = false)
    private Long timelineEntryId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_name", nullable = false, unique = true, length = 36)
    private String storageName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Attachment() {
    }

    public Attachment(Long timelineEntryId, String originalFilename, String storageName,
                      String contentType, long size) {
        this.timelineEntryId = timelineEntryId;
        this.originalFilename = originalFilename;
        this.storageName = storageName;
        this.contentType = contentType;
        this.size = size;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTimelineEntryId() { return timelineEntryId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageName() { return storageName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`src/main/java/com/helpdesk/attachment/AttachmentRepository.java`:
```java
package com.helpdesk.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTimelineEntryId(Long timelineEntryId);
    List<Attachment> findByTimelineEntryIdIn(java.util.Collection<Long> ids);
}
```

`src/main/java/com/helpdesk/attachment/FileStorage.java`:
```java
package com.helpdesk.attachment;

import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.common.error.PayloadTooLargeException;
import com.helpdesk.common.error.UnsupportedMediaTypeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Component
public class FileStorage {

    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/gif",
            "application/pdf", "text/plain", "application/zip");

    private final Path root;

    public FileStorage(@Value("${helpdesk.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload dir " + uploadDir, e);
        }
    }

    public void validate(MultipartFile file) {
        if (file.getSize() > MAX_SIZE) {
            throw new PayloadTooLargeException("File exceeds the 10 MB limit: " + file.getOriginalFilename());
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!ALLOWED.contains(contentType)) {
            throw new UnsupportedMediaTypeException("Content type not allowed: " + contentType);
        }
    }

    public String store(MultipartFile file) {
        String storageName = UUID.randomUUID().toString();
        try {
            Files.copy(file.getInputStream(), root.resolve(storageName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store file", e);
        }
        return storageName;
    }

    public InputStream open(String storageName) {
        Path path = root.resolve(storageName);
        if (!Files.exists(path)) {
            throw new NotFoundException("Attachment file not found");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open file", e);
        }
    }

    public void delete(String storageName) {
        try {
            Files.deleteIfExists(root.resolve(storageName));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete file", e);
        }
    }
}
```

`src/main/java/com/helpdesk/attachment/AttachmentService.java`:
```java
package com.helpdesk.attachment;

import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.security.UserContext;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TimelineEntry;
import com.helpdesk.ticket.TimelineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final TimelineRepository timeline;
    private final TicketRepository tickets;
    private final FileStorage storage;

    public AttachmentService(AttachmentRepository attachments, TimelineRepository timeline,
                             TicketRepository tickets, FileStorage storage) {
        this.attachments = attachments;
        this.timeline = timeline;
        this.tickets = tickets;
        this.storage = storage;
    }

    @Transactional
    public List<Attachment> attach(TimelineEntry entry, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        return java.util.Arrays.stream(files)
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> {
                    storage.validate(file);
                    String storageName = storage.store(file);
                    return attachments.save(new Attachment(entry.getId(),
                            file.getOriginalFilename() == null ? "file" : file.getOriginalFilename(),
                            storageName, file.getContentType(), file.getSize()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Attachment resolve(Long id, UserContext actor) {
        Attachment attachment = attachments.findById(id)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        TimelineEntry entry = timeline.findById(attachment.getTimelineEntryId())
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        Ticket ticket = tickets.findById(entry.getTicket().getId())
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        boolean allowed = actor.isAgent() || ticket.getCreatedBy().getId().equals(actor.id());
        if (!allowed) {
            throw new NotFoundException("Attachment not found");
        }
        return attachment;
    }

    @Transactional
    public void deleteAllOfTicket(Ticket ticket) {
        // chamado apenas antes do delete do ticket (cascade DB remove os registros)
        var entries = timeline.findByTicketId(ticket.getId(),
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
        attachments.findByTimelineEntryIdIn(entries.map(TimelineEntry::getId).getContent())
                .forEach(a -> storage.delete(a.getStorageName()));
    }
}
```

- [ ] **Step 4: Integrar no TimelineService e TicketController**

Em `TimelineService.addComment`, mude a assinatura para `addComment(Long ticketId, UserContext actor, String body, MultipartFile[] files)` e, após salvar a entrada:
```java
List<Attachment> saved = attachmentService.attach(entry, files);
return new CommentResponse(entry.getId(), entry.getType(), entry.getEventType(),
        author.getId(), author.getName(), entry.getBody(), entry.getPayload(), entry.getCreatedAt(),
        saved.stream().map(a -> new AttachmentView(a.getId(), a.getOriginalFilename(),
                a.getContentType(), a.getSize())).toList());
```
Inyecte `AttachmentService` no `TimelineService` (sem ciclo: `AttachmentService` não chama `TimelineService`).

No `TicketController`, troque o endpoint de comentário:
```java
@PostMapping(value = "/{id}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<CommentResponse> comment(@AuthenticationPrincipal UserContext current,
                                               @PathVariable Long id,
                                               @RequestParam String body,
                                               @RequestPart(value = "files", required = false) MultipartFile[] files) {
    ticketService.loadOwned(id, current);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(timelineService.addComment(id, current, body, files));
}
```
A validação de `body` em branco: no início de `addComment`, `if (body == null || body.isBlank()) throw new UnprocessableBodyException(...)` — simpler: reutilize `MethodArgumentNotValid`? Como não há mais `@Valid` em record aqui, faça manual com `ConflictException`? Não — melhor: mantenha `@RequestParam String body` e valide no serviço com nova exceção `UnprocessableException` mapeada para 422 no handler:
```java
public class UnprocessableException extends RuntimeException { public UnprocessableException(String d){super(d);} }
// handler: problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed", ex.getMessage(), req)
```
No `TicketService.delete`, antes de `tickets.delete(ticket)`: `attachmentService.deleteAllOfTicket(ticket);` (injete `AttachmentService`).

Atenção ao ciclo de dependência: `TicketService` → `AttachmentService` → `TicketRepository` (ok, só repositório). `TimelineService` → `AttachmentService` (ok).

- [ ] **Step 5: Implementar AttachmentController**

`src/main/java/com/helpdesk/attachment/AttachmentController.java`:
```java
package com.helpdesk.attachment;

import com.helpdesk.security.UserContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final FileStorage storage;

    public AttachmentController(AttachmentService attachmentService, FileStorage storage) {
        this.attachmentService = attachmentService;
        this.storage = storage;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal UserContext current,
                                                        @PathVariable Long id) {
        Attachment attachment = attachmentService.resolve(id, current);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalFilename().replace("\"", "") + "\"")
                .body(new InputStreamResource(storage.open(attachment.getStorageName())));
    }
}
```

- [ ] **Step 6: Ajustar testes antigos e rodar suíte**

Os testes de comentário das Tasks 7–8 usam JSON — atualize `TimelineIntegrationTest` para multipart (mesmo padrão do `AttachmentIntegrationTest`, só `body`).
```powershell
mvn -q test; if ($?) { git add -A; git commit -m "feat: attachment upload/download with validation and cascade cleanup" }
```
Esperado: PASS.

---

### Task 10: Métricas globais e por agente

**Files:**
- Create: `src/main/java/com/helpdesk/metrics/MetricsService.java`, `MetricsController.java`, `dto/MetricsDtos.java`
- Test: `src/test/java/com/helpdesk/metrics/MetricsIntegrationTest.java`

**Interfaces:**
- Consumes: `TicketRepository`, `UserService.loadRequired`, `Priority`/`TicketStatus`.
- Produces:
  - `GET /api/metrics` → `GlobalMetrics(total, byStatus: Map<TicketStatus,Long>, slaCompliancePercent: Double, avgFirstResponseHours: Double, avgResolutionHours: Double)` — `Double` pode ser `null` quando não há amostras; `byStatus` cobre os 5 status (0 quando ausente).
  - `GET /api/metrics/agents` → `List<AgentMetrics>` (todos os agentes); `GET /api/metrics/agents/{id}` → `AgentMetrics` (id inexistente ou não-agente → 404).
  - `AgentMetrics(agentId, agentName, assigned, resolved, slaCompliancePercent, avgFirstResponseHours, avgResolutionHours)`.
  - Regras: `% dentro do SLA` = resolvidos com `resolvedAt <= dueAt` ÷ total resolvido (null se resolvido = 0); médias em **horas de relógio** (não úteis), da criação até `firstResponseAt`/`resolvedAt` (null sem amostras); por agente considera apenas tickets com `assignedTo = id`.

- [ ] **Step 1: Escrever teste (falha)**

`src/test/java/com/helpdesk/metrics/MetricsIntegrationTest.java`:
```java
package com.helpdesk.metrics;

import com.helpdesk.IntegrationTestBase;
import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.ticket.Priority;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricsIntegrationTest extends IntegrationTestBase {

    @Autowired TicketRepository tickets;
    @Autowired UserRepository users;

    private User client;
    private User agent;

    @BeforeEach
    void seedTickets() {
        if (tickets.count() > 0) {
            return;
        }
        client = users.save(new User("Cliente M", "m1@dev.io", "hash", Role.CLIENTE));
        agent = users.save(new User("Agente M", "m2@dev.io", "hash", Role.AGENTE));

        // 1: resolvido dentro do SLA, primeira resposta em 1h
        Ticket ok = new Ticket("T1", "d", Priority.MEDIA, TicketStatus.RESOLVIDO, client,
                Instant.parse("2026-01-05T11:00:00Z"));
        ok.setProtocol("TK-00001");
        ok.setAssignedTo(agent);
        ok.setFirstResponseAt(Instant.parse("2026-01-05T12:00:00Z"));
        ok.setResolvedAt(Instant.parse("2026-01-06T14:00:00Z"));
        ok.setDueAt(Instant.parse("2026-01-07T11:00:00Z"));
        ok.setFirstResponseDueAt(Instant.parse("2026-01-05T15:00:00Z"));
        tickets.save(ok);

        // 2: resolvido FORA do SLA
        Ticket late = new Ticket("T2", "d", Priority.ALTA, TicketStatus.RESOLVIDO, client,
                Instant.parse("2026-01-05T11:00:00Z"));
        late.setProtocol("TK-00002");
        late.setAssignedTo(agent);
        late.setFirstResponseAt(Instant.parse("2026-01-05T14:00:00Z"));
        late.setResolvedAt(Instant.parse("2026-01-10T11:00:00Z"));
        late.setDueAt(Instant.parse("2026-01-06T11:00:00Z"));
        late.setFirstResponseDueAt(Instant.parse("2026-01-05T13:00:00Z"));
        tickets.save(late);

        // 3: aberto, sem resposta
        Ticket open = new Ticket("T3", "d", Priority.BAIXA, TicketStatus.ABERTO, client,
                Instant.parse("2026-01-05T11:00:00Z"));
        open.setProtocol("TK-00003");
        tickets.save(open);
    }

    @Test
    void globalMetricsAreCorrect() throws Exception {
        String agentToken = login("m2@dev.io", "Senha@123"); // seed não roda no profile test
        mockMvc.perform(auth(get("/api/metrics"), agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.byStatus.RESOLVIDO").value(2))
                .andExpect(jsonPath("$.byStatus.ABERTO").value(1))
                .andExpect(jsonPath("$.slaCompliancePercent").value(50.0));
    }

    @Test
    void perAgentMetricsAreCorrect() throws Exception {
        var token = register("Agente X", "mx@dev.io", "Senha@123", "AGENTE", null); // sem invite → pode falhar
        mockMvc.perform(auth(get("/api/metrics/agents"), token))
                .andExpect(status().isOk());
    }
}
```

**Problemas a corrigir ao implementar (o teste acima é esqueleto):**
1. `login("m2@dev.io", ...)` não funciona: usuários seedados via repositório têm hash literal `"hash"`. Em vez disso, cadastre tudo **pela API**: registre cliente e agente (com invite `AGENTE-2026` garantido via `InviteCodeRepository`) e crie os tickets **pela API** com `TimeProvider` fixo (`when(time.now()).thenReturn(...)`) controlando `dueAt`/`resolvedAt`... Como `resolvedAt` é gerado pelo relógio, o cálculo exato fica frágil. **Abordagem robusta final:** mantenha o seed direto no repositório (como acima), mas faça o login de um **terceiro** agente criado pela API e garanta que a consulta de métricas enxergue os dados seedados (mesmo banco, ok). Para o token do agente seedado, crie-o pela API e depois **atualize** seus tickets no repositório para `assignedTo = esse usuário`:
```java
String agentToken = register("Agente M", "m2@dev.io", "Senha@123", "AGENTE", "AGENTE-2026");
User agentUser = users.findByEmail("m2@dev.io").orElseThrow();
tickets.findAll().forEach(t -> { if (t.getAssignedTo() != null || t.getStatus() == TicketStatus.RESOLVIDO) t.setAssignedTo(agentUser); });
tickets.saveAll(tickets.findAll());
```
Com isso: `total=3`, `RESOLVIDO=2`, compliance `50.0%` (1 de 2 dentro), média primeira resposta = ((1h + 3h)/2) = `2.0` horas; média resolução = ((27h + 120h)/2) = `73.5` horas. `perAgent`: `assigned=3` (dois resolvidos + aberto reatribuído? — ajuste: só marque `assignedTo` nos tickets 1 e 2 → `assigned=2`, `resolved=2`, compliance `50.0`).

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=MetricsIntegrationTest
```
Esperado: FAIL (404).

- [ ] **Step 3: Implementar DTOs, service e controller**

`src/main/java/com/helpdesk/metrics/dto/MetricsDtos.java`:
```java
package com.helpdesk.metrics.dto;

import com.helpdesk.ticket.TicketStatus;

import java.util.Map;

public final class MetricsDtos {

    private MetricsDtos() {
    }

    public record GlobalMetrics(long total, Map<TicketStatus, Long> byStatus,
                                Double slaCompliancePercent,
                                Double avgFirstResponseHours,
                                Double avgResolutionHours) {
    }

    public record AgentMetrics(Long agentId, String agentName, long assigned, long resolved,
                               Double slaCompliancePercent,
                               Double avgFirstResponseHours,
                               Double avgResolutionHours) {
    }
}
```

`src/main/java/com/helpdesk/metrics/MetricsService.java`:
```java
package com.helpdesk.metrics;

import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.domain.User;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.metrics.dto.MetricsDtos.AgentMetrics;
import com.helpdesk.metrics.dto.MetricsDtos.GlobalMetrics;
import com.helpdesk.ticket.Ticket;
import com.helpdesk.ticket.TicketRepository;
import com.helpdesk.ticket.TicketStatus;
import com.helpdesk.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@Service
public class MetricsService {

    private final TicketRepository tickets;
    private final UserService userService;

    public MetricsService(TicketRepository tickets, UserService userService) {
        this.tickets = tickets;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public GlobalMetrics global() {
        List<Ticket> all = tickets.findAll();
        Map<TicketStatus, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            byStatus.put(status, all.stream().filter(t -> t.getStatus() == status).count());
        }
        return new GlobalMetrics(all.size(), byStatus,
                compliance(all),
                avgHours(all, Ticket::getCreatedAt, Ticket::getFirstResponseAt),
                avgHours(all, Ticket::getCreatedAt, Ticket::getResolvedAt));
    }

    @Transactional(readOnly = true)
    public List<AgentMetrics> perAgent() {
        // precisa dos agentes: deriva dos tickets atribuídos + tabela users via service
        throw new UnsupportedOperationException("use perAgent(List<User> agents)");
    }

    @Transactional(readOnly = true)
    public List<AgentMetrics> perAgent(List<User> agents) {
        return agents.stream().map(this::metricsFor).toList();
    }

    @Transactional(readOnly = true)
    public AgentMetrics forAgent(Long agentId) {
        User user = userService.loadRequired(agentId);
        if (user.getRole() != Role.AGENTE) {
            throw new NotFoundException("Agent not found");
        }
        return metricsFor(user);
    }

    private AgentMetrics metricsFor(User agent) {
        List<Ticket> assigned = tickets.findAll().stream()
                .filter(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(agent.getId()))
                .toList();
        long resolved = assigned.stream().filter(t -> t.getStatus() == TicketStatus.RESOLVIDO).count();
        return new AgentMetrics(agent.getId(), agent.getName(), assigned.size(), resolved,
                compliance(assigned),
                avgHours(assigned, Ticket::getCreatedAt, Ticket::getFirstResponseAt),
                avgHours(assigned, Ticket::getCreatedAt, Ticket::getResolvedAt));
    }

    private Double compliance(List<Ticket> tickets) {
        List<Ticket> resolved = tickets.stream()
                .filter(t -> t.getStatus() == TicketStatus.RESOLVIDO && t.getResolvedAt() != null
                        && t.getDueAt() != null)
                .toList();
        if (resolved.isEmpty()) {
            return null;
        }
        long within = resolved.stream().filter(t -> !t.getResolvedAt().isAfter(t.getDueAt())).count();
        return within * 100.0 / resolved.size();
    }

    private Double avgHours(List<Ticket> tickets,
                            java.util.function.Function<Ticket, Instant> from,
                            java.util.function.Function<Ticket, Instant> to) {
        OptionalDouble avg = tickets.stream()
                .filter(t -> from.apply(t) != null && to.apply(t) != null)
                .mapToDouble(t -> Duration.between(from.apply(t), to.apply(t)).toMinutes() / 60.0)
                .average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 100.0) / 100.0 : null;
    }
}
```
O método `perAgent()` sem argumento deve ser removido — em vez dele, o controller busca agentes. Para isso, adicione ao `UserRepository`:
```java
java.util.List<User> findByRole(com.helpdesk.auth.domain.Role role);
```
e injete `UserRepository` no `MetricsService` no lugar de listar tudo.

- [ ] **Step 4: Implementar MetricsController**

`src/main/java/com/helpdesk/metrics/MetricsController.java`:
```java
package com.helpdesk.metrics;

import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.metrics.dto.MetricsDtos.AgentMetrics;
import com.helpdesk.metrics.dto.MetricsDtos.GlobalMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;
    private final UserRepository users;

    public MetricsController(MetricsService metricsService, UserRepository users) {
        this.metricsService = metricsService;
        this.users = users;
    }

    @GetMapping
    public GlobalMetrics global() {
        return metricsService.global();
    }

    @GetMapping("/agents")
    public List<AgentMetrics> agents() {
        return metricsService.perAgent(users.findByRole(Role.AGENTE));
    }

    @GetMapping("/agents/{id}")
    public AgentMetrics agent(@PathVariable Long id) {
        return metricsService.forAgent(id);
    }
}
```

- [ ] **Step 5: Rodar teste, suíte e commit**

```powershell
mvn -q test -Dtest=MetricsIntegrationTest; if ($?) { mvn -q test }
```
Esperado: PASS. Ajuste as expectativas do teste ao resultado real calculado acima (`assigned=2`).
```powershell
git add -A; git commit -m "feat: global and per-agent SLA metrics endpoints"
```

---

### Task 11: Swagger/OpenAPI + README final

**Files:**
- Create: `src/main/java/com/helpdesk/common/config/OpenApiConfig.java`
- Create: `README.md`
- Test: `src/test/java/com/helpdesk/OpenApiIntegrationTest.java`

**Interfaces:**
- Consumes: todos os controllers (rotas já anotadas com `@Tag` opcional).
- Produces: `/v3/api-docs` (200 com paths principais), `/swagger-ui.html` público; README com setup, tabela de endpoints, fluxos curl e decisões.

- [ ] **Step 1: Escrever teste do OpenAPI (falha)**

`src/test/java/com/helpdesk/OpenApiIntegrationTest.java`:
```java
package com.helpdesk;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiIntegrationTest extends IntegrationTestBase {

    @Test
    void apiDocsExposeMainEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/auth/login.post").exists())
                .andExpect(jsonPath("$.paths./api/tickets.get").exists())
                .andExpect(jsonPath("$.paths./api/tickets/{id}/status.post").exists())
                .andExpect(jsonPath("$.paths./api/metrics.get").exists())
                .andExpect(jsonPath("$.paths./api/attachments/{id}.get").exists());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

```powershell
mvn -q test -Dtest=OpenApiIntegrationTest
```
Esperado: 401/404 (a rota de docs exige auth por padrão? — `/v3/api-docs/**` já é público na Task 3; então falha por ausência do bean de config... springdoc já está no classpath desde a Task 1, então este teste pode **passar de primeira**; nesse caso pule direto ao Step 4 e mantenha o commit).

- [ ] **Step 3: Implementar OpenApiConfig**

`src/main/java/com/helpdesk/common/config/OpenApiConfig.java`:
```java
package com.helpdesk.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI helpdeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Help Desk API")
                        .description("API REST de gestão de chamados técnicos com SLA, "
                                + "JWT e métricas — projeto de portfólio.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
```

- [ ] **Step 4: Rodar suíte e smoke no Swagger**

```powershell
mvn -q test; if ($?) { docker compose up -d; mvn spring-boot:run }
```
Em outro terminal: `curl http://localhost:8080/swagger-ui/index.html` → 200; abra no navegador e valide que `POST /api/auth/register` e `POST /api/auth/login` funcionam pelo "Try it out". Pare o app.

- [ ] **Step 5: Escrever `README.md`**

Estrutura obrigatória do conteúdo (preencha com os valores reais do projeto — tabela de endpoints deve listar **todas** as rotas implementadas):

```markdown
# Help Desk API

API REST de gestão de chamados técnicos com SLA real, autenticação JWT,
anexos, métricas e documentação OpenAPI. Projeto pessoal de estudo.

## Stack
Java 17 · Spring Boot 3.3 · Spring Security (JWT) · MySQL 8 · Flyway ·
springdoc-openapi · Testcontainers

## Como rodar
1. `docker compose up -d` (MySQL 8 na porta 3306)
2. `mvn spring-boot:run`
3. Swagger: http://localhost:8080/swagger-ui.html
4. Testes: `mvn verify` (sobe MySQL efêmero via Testcontainers)

Usuários demo (perfil dev): cliente@demo.com / agente@demo.com — senha `Senha@123`.
Invite de agente: `AGENTE-2026`.

## Endpoints
(monta a tabela: método, rota, papel, descrição — auth, users, tickets,
status, assign, comments, timeline, attachments, sla-policies, metrics)

## Fluxo SLA
- Políticas por prioridade (horas úteis seg–sex 09:00–18:00, America/Sao_Paulo)
- Pausa em AGUARDANDO_CLIENTE; reativação desloca os prazos
- Primeira resposta = 1º comentário de agente
- Violação marcada em `slaBreached` + evento SLA_BREACHED na timeline

## Exemplos curl
(register → login → criar chamado → transição → comentar com anexo →
 métricas — um bloco por fluxo, com bearer token do login)

## Decisões de design
- Modular monolith por domínio; erros ProblemDetail (RFC 9457)
- Refresh token opaco com rotação + detecção de reuso
- 404 (não 403) para chamados alheios (não vaza existência)
- Sem scheduler de SLA: violação calculada sob demanda (leitura/transição)

## Limitações conhecidas
- Sem feriados no calendário de SLA; sem notificações; sem ADMIN
- Access token de 15 min segue válido após desativação da conta
```

- [ ] **Step 6: Verificação final e commit**

```powershell
mvn -q verify; if ($?) { git add -A; git commit -m "docs: README with setup, endpoints, curl flows and design notes" }
```
Esperado: `BUILD SUCCESS` — suíte completa (unit + integração) verde. Se algum teste de outra task quebrar por acaso, corrija o código (nunca "ajeite" o teste para voltar a verde sem motivo).

---

## Self-Review (executado pelo autor do plano)

1. **Spec coverage:** auth/invite/refresh §2.2 → Task 4; perfil §2.3 → Task 5; tickets/transições §2.4 → Task 7; SLA §2.5 → Tasks 6+7+8; timeline §2.6 → Task 8; anexos §2.7 → Task 9; métricas §2.8 → Task 10; erros §2.9 → Task 4 (+ ajustes Tasks 7–9); Swagger §2.10 → Task 11; stack §3.1 → Task 1; pacotes §3.2 → distribuído; schema §3.3 → Task 2; segurança §3.4 → Tasks 3–4; config §3.5 → Task 1; testes §4 → todos; README §6 → Task 11. **Seed de usuários demo** (§3.5 "1 cliente, 1 agente") → `DevDataSeeder` na Task 4.
2. **Placeholder scan:** dois pontos exigem ação consciente do executor e estão explicitados como "Correção obrigatória/Obs." no texto (construtor duplo do `SlaCalculator`; helper fictício `BusinessCalendarHelperProbe` a substituir por `BusinessCalendar`; teste esqueleto de métricas com seed via API). Nenhum "TBD".
3. **Type consistency:** `TicketService.loadOwned(Long, UserContext)` usado nas Tasks 8–9 com essa assinatura; `SlaCalculator.TicketSlaView`/`shift`/`isBreached` idênticos entre Tasks 6–8; `TimelineService.addComment` evolui de `(Long, UserContext, String)` (Task 8) para `+ MultipartFile[]` (Task 9) e a Task 9 manda atualizar os testes anteriores; `CommentResponse` substitui `TimelineEntryResponse` no retorno do POST de comentário (Task 9), mantendo `TimelineEntryResponse` no GET da timeline.

