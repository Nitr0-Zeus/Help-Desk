# Help Desk API — Design Spec

**Data:** 2026-10-03
**Status:** aprovado pelo usuário (Seções 1–3)
**Intenção:** projeto pessoal de portfólio/estudo — API REST de gestão de chamados técnicos com SLA real, backend seguro e documentado.

---

## 1. Contexto e objetivos

API REST completa para help desk com Java 17, Spring Boot 3, MySQL, autenticação JWT e documentação Swagger, aplicando conceitos reais de SLA (pausa, primeira resposta, violação, métricas).

**Sucesso significa:** `mvn verify` verde (unitários + integrações com Testcontainers MySQL), API subindo com `docker compose up -d` + `mvn spring-boot:run`, Swagger acessível, e README com fluxos curl suficiente para um recrutador entender o projeto sozinho.

**Não é:** frontend, notificações por e-mail, microserviços, gestão multi-tenant.

## 2. Escopo funcional

### 2.1 Papéis

Dois papéis apenas: `CLIENTE` e `AGENTE`. Não há `ADMIN`.

- **CLIENTE**: registra-se livremente, cria chamados, vê apenas os seus, comenta, responde (reativa SLA), edita/desativa o próprio perfil.
- **AGENTE**: só é criado com **invite code** (seedado no banco; endpoint de geração fica fora de escopo — sem ADMIN não há quem gere). Vê todos os chamados, assume, comenta, resolve, fecha. Métricas por agente são públicas entre autenticados.

### 2.2 Autenticação (`/api/auth`)

- `POST /api/auth/register` — `{name, email, password, role, inviteCode?}`; `role=CLIENTE` não exige código; `role=AGENTE` exige invite code válido e não expirado (marca como usado).
- `POST /api/auth/login` — devolve **access token JWT (15 min)** e **refresh token opaco (7 dias)**.
- `POST /api/auth/refresh` — rotaciona: valida o refresh, revoga o antigo, emite novo (token usado duas vezes → revoga toda a família e retorna 401).
- `POST /api/auth/logout` — revoga o refresh token atual.
- Refresh tokens guardados como **hash SHA-256** no banco (`refresh_tokens`), com `expires_at`, `revoked`, `replaced_by`, `user_id`.

### 2.3 Perfil (`/api/users/me`)

- `GET /users/me`, `PATCH /users/me` (name/email), `POST /users/me/password` (senha atual + nova, BCrypt), `DELETE /users/me` (desativação soft: `active=false`, login passa a falhar).

### 2.4 Chamados (`/api/tickets`)

Campos: `id`, `protocol` (ex.: `TK-00001`, gerado na criação), `title`, `description`, `priority` (`BAIXA|MEDIA|ALTA|CRITICA`), `status`, `createdBy`, `assignedTo?`, timestamps (`createdAt`, `firstResponseAt?`, `waitingSince?`, `resolvedAt?`, `closedAt?`), campos de SLA (`firstResponseDueAt?`, `dueAt?`, `slaBreached`).

- `POST /tickets` — qualquer papel autenticado cria; `createdBy` = autenticado. `CLIENTE` vira dono (visibilidade restrita); `AGENTE` cria já com `assignedTo = si` e status inicial `EM_ATENDIMENTO`. Chamado de cliente inicia `ABERTO`.
- `GET /tickets` — `CLIENTE` vê só os próprios; `AGENTE` vê todos. Filtros: `status`, `priority`, `slaBreached`, `assignedTo`, `q` (busca em título/descrição/protocol). Paginação `page/size/sort` (size máx. 100).
- `GET /tickets/{id}` — dono ou agente; `404` para quem não tem acesso (não `403`, para não vazar existência).
- `PATCH /tickets/{id}` — dono edita `title/description/priority` **apenas em `ABERTO`**; agente pode sempre (exceto em `FECHADO`).
- `DELETE /tickets/{id}` — apenas dono e apenas em `ABERTO` (soft delete não; remoção física com cascade de timeline/anexos removendo também os arquivos).

**Transições de status** (`POST /tickets/{id}/status`, body `{status, comment?}`):

```
ABERTO            → EM_ATENDIMENTO      (agente assume; torna-se assignedTo)
EM_ATENDIMENTO    → AGUARDANDO_CLIENTE  (agente pede informação; pausa SLA)
AGUARDANDO_CLIENTE→ EM_ATENDIMENTO      (cliente comenta OU agente transiciona; reativa SLA)
EM_ATENDIMENTO    → RESOLVIDO           (agente resolve; congela tempos de SLA)
RESOLVIDO         → FECHADO             (agente ou dono fecha)
```

- Transição inválida → `409 CONFLICT` com ProblemDetail.
- Toda transição gera **evento de auditoria** na timeline (`STATUS_CHANGED`, `ASSIGNED`, `SLA_PAUSED`, `SLA_RESUMED`, `SLA_BREACHED`).
- `POST /tickets/{id}/assign` — agente se autoatribui (ou atribui a outro agente) apenas de `ABERTO`/`EM_ATENDIMENTO`; libera com `assignedTo=null`.

### 2.5 SLA (`/api/sla-policies`)

- Tabela `sla_policies`: `priority` única, `firstResponseHours`, `resolutionHours`, `active`. Seed cria as 4 políticas. `GET /sla-policies` (leitura autenticada); **edição via SQL/seed** (sem ADMIN, sem endpoint de escrita).
- **Cálculo**: horário comercial simples — seg–sex 09:00–18:00 (9 h/dia útil, sem pausa de almoço), sem feriados. Função `BusinessCalendar` converte hora simples ↔ ponto no tempo; escopo de horas ignorado fora da janela.
- **Pausa**: ao entrar em `AGUARDANDO_CLIENTE` registra `waitingSince`; ao reativar, o tempo decorrido em horário comercial é **deslocado** para `firstResponseDueAt` e `dueAt` (e re-verifica violação).
- **Primeira resposta**: primeiro **comentário de agente** (não evento) marca `firstResponseAt` e congela `firstResponseDueAt`.
- **Violação**: `slaBreached=true` quando agora > `dueAt` sem resolver, ou > `firstResponseDueAt` sem primeira resposta; ao violar, gera evento `SLA_BREACHED` na timeline (uma vez). Prazos e violações recalculados sob demanda (leitura/transition) — **sem scheduler** (YAGNI; anotado como extensão futura).
- Ao resolver: congela `resolvedAt`; ao fechar a partir de `RESOLVIDO`: nada muda nos campos de SLA.

### 2.6 Timeline — comentários + auditoria (`/tickets/{id}/timeline`)

Tabela única `timeline_entries` com `type`:

- `COMMENT` — `{author, body, createdAt}`; qualquer papel com acesso comenta (`POST /tickets/{id}/comments`). Comentário de cliente num chamado em `AGUARDANDO_CLIENTE` dispara a reativação automática.
- `EVENT` — `{eventType, actor, payload JSON, createdAt}`; gerado pelo sistema.

`GET /tickets/{id}/timeline` — paginado, ordenado por `createdAt` desc.

### 2.7 Anexos

- Upload **em multipart dentro do comentário**: `POST /tickets/{id}/comments` aceita `body` + `files[]` (opcional). Cada arquivo vira registro `attachments` (`originalFilename`, `storageName` UUID, `contentType`, `size`, `timelineEntryId`).
- Armazenamento: pasta `uploads/` (configurável por `HELPDESK_UPLOAD_DIR`), `storageName` no disco.
- Download: `GET /attachments/{id}` — só quem tem acesso ao chamado pai. Limites: 10 MB/arquivo, tipos: imagens/PDF/txt/zip (whitelist no `ContentTypeInspector`).
- Remover chamado remove registros + arquivos do disco.

### 2.8 Métricas (`/api/metrics`)

- `GET /metrics` (global): total de chamados, contagem por status, % dentro do SLA (resolvidos cujo `dueAt` >= `resolvedAt`), tempo médio de primeira resposta e de resolução (horas de relógio, não úteis — documentado).
- `GET /metrics/agents` (lista) e `GET /metrics/agents/{id}`: por agente — atribuídos, resolvidos, % dentro do SLA, médias de primeira resposta/resolução.
- Visíveis a qualquer autenticado.

### 2.9 Erros e validação

- Todos os erros no formato **RFC 9457 `ProblemDetail`** (`type`, `title`, `status`, `detail`, `instance`, extras como `violations`).
- Códigos: `400` corpo malformado, `401` não autenticado/token inválido, `403` autenticado sem papel, `404` inexistente/sem acesso, `409` transição inválida/recurso em estado errado/protocolo duplicado, `413` anexo grande, `415` tipo não suportado, `422` validação de campo (`violations`), `500` inesperado (trace id).
- Bean Validation (`@Valid`) em todos os DTOs de entrada.

### 2.10 Documentação

- `springdoc-openui` (`springdoc-openapi-starter-webmvc-ui`), Swagger UI em `/swagger-ui.html`, OpenAPI em `/v3/api-docs`.
- Security config permite essas rotas sem autenticação; endpoint `POST /auth/login` documenta o bearer scheme. Tags por módulo.

## 3. Arquitetura técnica

### 3.1 Stack

| Item | Escolha |
|---|---|
| Linguagem | Java 17 (`maven.compiler.release=17`), compilado no JDK 21 instalado |
| Framework | Spring Boot 3.3.x (parent), Spring Web, Data JPA, Security, Validation, Actuator |
| Banco | MySQL 8 via `docker-compose.yml` (porta 3306, volume nomeado) |
| Migrações | Flyway (`V1__init.sql`, `V2__seed.sql`) |
| JWT | jjwt 0.12.x (mesmo padrão do projeto `finance` do usuário) |
| Doc | springdoc-openapi 2.x |
| Testes | JUnit 5, Mockito, Spring Security Test, Testcontainers MySQL |
| Build | Maven instalado via winget + Maven Wrapper no repo |
| DTOs | Java `records` (sem Lombok no domínio) |
| Clone | Docker disponível no ambiente |

### 3.2 Estrutura de pacotes — modular monolith

Raiz `com.helpdesk`:

```
common/      ProblemDetail, GlobalExceptionHandler, exceptions, pagination
security/    JwtService, JwtAuthFilter, SecurityConfig, UserContext
auth/        AuthController, AuthService, InviteCode, RefreshToken
user/        UserController, UserService, ProfileDTOs
ticket/      TicketController, TicketService, TicketStatus transitions,
             TimelineController (comments+events), Assign
sla/         SlaPolicy, SlaService, BusinessCalendar, SlaPolicyController
attachment/  AttachmentController, AttachmentService, storage/ (FileStorage)
metrics/     MetricsController, MetricsService
```

Regras de fronteira:

- Controllers acessam **apenas** o serviço do próprio módulo.
- Entre módulos, dependência só via **interface pública** do módulo dono (ex.: `ticket` usa `SlaService` de `sla`; `attachment` recebe `timelineEntryId` de `ticket`).
- `common` e `security` são folhas: não dependem de módulos de domínio.
- `sla` não conhece `ticket` (trabalha com enums/IDs primitivos).

### 3.3 Modelo de dados (V1__init.sql)

```
users(id PK, name, email UNIQUE, password_hash, role ENUM(CLIENTE,AGENTE),
      active BOOL, created_at, updated_at)

invite_codes(code PK, role, expires_at, used_by FK→users NULL, used_at)

refresh_tokens(id PK, user_id FK, token_hash UNIQUE, expires_at,
      revoked BOOL, replaced_by NULL, created_at)

sla_policies(id PK, priority ENUM UNIQUE, first_response_hours,
      resolution_hours, active BOOL)

tickets(id PK, protocol UNIQUE, title, description,
      priority ENUM, status ENUM(ABERTO,EM_ATENDIMENTO,AGUARDANDO_CLIENTE,RESOLVIDO,FECHADO),
      created_by FK→users, assigned_to FK→users NULL,
      created_at, updated_at, first_response_at NULL, waiting_since NULL,
      resolved_at NULL, closed_at NULL,
      first_response_due_at NULL, due_at NULL, sla_breached BOOL)

timeline_entries(id PK, ticket_id FK, type ENUM(COMMENT,EVENT),
      event_type ENUM NULL, author_id FK→users NULL, body TEXT NULL,
      payload JSON NULL, created_at)

attachments(id PK, timeline_entry_id FK, original_filename, storage_name UNIQUE,
      content_type, size BIGINT, created_at)
```

Índices: `tickets(status)`, `tickets(created_by)`, `tickets(assigned_to)`, `timeline_entries(ticket_id, created_at)`, `refresh_tokens(token_hash)`.

### 3.4 Segurança

- Filter chain stateless; JWT no header `Authorization: Bearer`.
- Access token: HS256 com segredo de env (`HELPDESK_JWT_SECRET`, min. 32 bytes), claims: `sub` (id), `role`, `email`, `jti`, `exp`.
- Refresh: opaco (32 bytes aleatórios, Base64URL), guardado como hash.
- `@PreAuthorize` nos controllers (`hasRole('AGENTE')` etc.) + checagem de dono no serviço.
- BCrypt strength 10. CORS dev: `localhost:3000`/`5173` (pronto pra um futuro front).

### 3.5 Configuração

- `application.yml` + `application-dev.yml`/`application-test.yml`; tudo sensível por env (`HELPDESK_DB_*`, `HELPDESK_JWT_SECRET`, `HELPDESK_UPLOAD_DIR`).
- Dev tem defaults locais; test sobe com Testcontainers (property `spring.datasource.url` dinâmica).
- `docker-compose.yml` só com MySQL 8 (app roda na máquina).

## 4. Testes

**Unitários** (sem Spring):

- `BusinessCalendar` — conversões de horário comercial, fim de semana, overnight edges.
- `SlaService` — cálculo de prazo, deslocamento por pausa, violação, primeira resposta.
- Transições de `TicketStatus` — matriz completa permitida/negada.
- `JwtService` — geração/validação/expiração/role claim.

**Integração** (`@SpringBootTest` + Testcontainers MySQL, MockMvc):

1. Auth: registro cliente; registro agente com/sem invite (sucesso, código errado, expirado, reuso); login; refresh com rotação; reuso de refresh → 401 + família revogada; logout; senha errada → 401.
2. Autorização: cliente não vê chamado alheio (404); cliente sem papel de agente → 403; sem token → 401.
3. Ticket: criação + protocolo único; filtros/paginação; edição nas regras de estado.
4. Fluxo SLA: cria (com prazos calculados em horário comercial) → agente assume → comenta (primeira resposta congela) → `AGUARDANDO_CLIENTE` (pausa) → cliente comenta (reativa, prazos deslocados) → resolve → força violação via `Clock` fixo → `slaBreached`.
5. Anexos: upload em comentário, download permitido/negado, 415/413, remoção cascade.
6. Métricas: cenário montado → números conferidos (%, médias).
7. Swagger: `/v3/api-docs` 200 e contém os paths principais.

`Clock` injetável nos serviços de SLA para testes determinísticos.

## 5. Fora de escopo (YAGNI)

- Frontend, notificações (e-mail/webhook), feriados no SLA, scheduler/escalonamento automático, geração de invite codes por API, RBAC além dos 2 papéis, S3, i18n, dockerização do app, CI/CD.

## 6. Entrega

- Repo git local em `C:\Users\User\Desktop\helpdesk-api` (commit da spec e de cada fase).
- README: visão geral, diagrama de endpoints (tabela), setup (compose + run), fluxos curl (registro → login → criar chamado → transições → anexos → métricas), decisões de design e limitações conhecidas.
