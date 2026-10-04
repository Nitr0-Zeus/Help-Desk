# Help Desk API

API REST de gestão de chamados técnicos com **SLA real** (horas úteis, pausa em
aguardando-cliente, violação auditada), autenticação **JWT**, anexos, métricas e
documentação **OpenAPI**. Projeto pessoal de estudo — backend completo, seguro e
documentado, pensado para portfólio.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 17 (compilado no JDK 21) |
| Framework | Spring Boot 3.3 (Web, Data JPA, Security, Validation, Actuator) |
| Banco | SQLite (arquivo, sem Docker) + Spring SQL Init (`schema.sql`/`data.sql`) |
| Auth | JWT access (15 min) + refresh token opaco (7 dias, rotação + detecção de reuso) |
| Doc | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, Mockito, MockMvc, AssertJ (70+ testes) |

## Como rodar

```bash
cd frontend && npm install && npm run build   # gera a SPA em src/main/resources/static/
mvn spring-boot:run
```

Pronto — a aplicação cria `helpdesk.db` no diretório do projeto, aplica o schema
e o seed automaticamente, e serve a **interface web** (frontend React) na mesma porta.

- **Interface**: http://localhost:8080 (dev com hot reload: `cd frontend && npm run dev` → http://localhost:5173)
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Testes backend: `mvn verify` · Testes frontend: `cd frontend && npm test`

### Usuários demo (perfil `dev`, o padrão)

| Email | Papel | Senha |
|---|---|---|
| `cliente@demo.com` | CLIENTE | `Senha@123` |
| `agente@demo.com` | AGENTE | `Senha@123` |

Invite para se registrar como agente: **`AGENTE-2026`**.

### Variáveis de ambiente (opcionais)

| Variável | Default |
|---|---|
| `HELPDESK_DB_URL` | `jdbc:sqlite:helpdesk.db` |
| `HELPDESK_JWT_SECRET` | secret de dev (≥32 bytes; **troque em produção**) |
| `HELPDESK_UPLOAD_DIR` | `./uploads` |

## Endpoints

Todos exigem `Authorization: Bearer <accessToken>`, exceto `/api/auth/**` e as
rotas de documentação.

### Auth (público)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | Registro. `CLIENTE` livre; `AGENTE` exige `inviteCode` |
| POST | `/api/auth/login` | Retorna access + refresh token |
| POST | `/api/auth/refresh` | Rotaciona o refresh token (reuso revoga a família) |
| POST | `/api/auth/logout` | Revoga o refresh token |

### Perfil

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/users/me` | Próprio perfil |
| PATCH | `/api/users/me` | Atualiza nome/email |
| POST | `/api/users/me/password` | Troca senha (exige a atual) |
| DELETE | `/api/users/me` | Desativa conta (soft delete) |

### Chamados

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| POST | `/api/tickets` | ambos | Cria chamado (agente cria já em atendimento) |
| GET | `/api/tickets` | ambos | Lista; cliente vê só os seus. Filtros: `status`, `priority`, `slaBreached`, `q`, `page`, `size`, `sort` |
| GET | `/api/tickets/{id}` | dono/agente | Detalhe (sem acesso → 404) |
| PATCH | `/api/tickets/{id}` | dono/agente | Edita (cliente só em `ABERTO`) |
| DELETE | `/api/tickets/{id}` | dono | Deleta (só em `ABERTO`) |
| POST | `/api/tickets/{id}/status` | dono/agente | Transição de status (regras abaixo) |
| POST | `/api/tickets/{id}/assign` | agente | Autoatribui (ou `{"agentId": n}`) |
| DELETE | `/api/tickets/{id}/assign` | agente | Libera o chamado |
| GET | `/api/tickets/{id}/timeline` | dono/agente | Comentários + eventos (paginado) |
| POST | `/api/tickets/{id}/comments` | dono/agente | Comenta (**multipart**: campo `body` + arquivos `files` opcionais) |

### Anexos, SLA e métricas

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/attachments/{id}` | Download (quem tem acesso ao chamado) |
| GET | `/api/sla-policies` | Políticas por prioridade |
| GET | `/api/metrics` | Resumo global (SLA %, médias, por status) |
| GET | `/api/metrics/agents` | Métricas por agente |
| GET | `/api/metrics/agents/{id}` | Métricas de um agente (404 se não for agente) |

## Ciclo de vida e SLA

```
ABERTO ──► EM_ATENDIMENTO ──► AGUARDANDO_CLIENTE ──► EM_ATENDIMENTO ──► RESOLVIDO ──► FECHADO
 (cliente cria)   (assume)        (pausa SLA)          (reativa)         (resolve)    (dono/agente fecha)
```

- Transições fora da matriz → `409 Conflict`. Cliente só fecha (e só o próprio).
- **Horário útil**: seg–sex, 09:00–18:00 (`America/Sao_Paulo`), sem feriados.
- **Políticas** (primeira resposta / resolução, em horas úteis): BAIXA 8/72,
  MEDIA 4/48, ALTA 2/24, CRITICA 1/8.
- **Pausa**: em `AGUARDANDO_CLIENTE` o relógio para; ao reativar (comentário do
  cliente), os prazos são deslocados pelos minutos úteis pausados — evento
  `SLA_RESUMED` na timeline.
- **Primeira resposta** = primeiro comentário de agente.
- **Violação**: calculada sob demanda (leitura/transição); `slaBreached=true` +
  evento único `SLA_BREACHED` na timeline.
- Timeline única: comentários (`COMMENT`) e auditoria (`STATUS_CHANGED`,
  `ASSIGNED`, `SLA_PAUSED`, `SLA_RESUMED`, `SLA_BREACHED`).

## Exemplos curl

```bash
BASE=http://localhost:8080/api

# registrar cliente
curl -s -X POST $BASE/auth/register -H "Content-Type: application/json" -d '{
  "name":"Ana","email":"ana@dev.io","password":"Senha@123","role":"CLIENTE"}'

# login (access + refreshToken)
curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ana@dev.io","password":"Senha@123"}'

TOKEN=<accessToken do login>

# criar chamado
curl -s -X POST $BASE/tickets -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{
  "title":"Impressora não imprime","description":"Fila zerada","priority":"MEDIA"}'

# listar com filtros
curl -s "$BASE/tickets?status=ABERTO&priority=MEDIA&size=10" -H "Authorization: Bearer $TOKEN"

# agente assume e resolve (troque o token)
curl -s -X POST $BASE/tickets/1/status -H "Authorization: Bearer $AGENTE_TOKEN" \
  -H "Content-Type: application/json" -d '{"status":"EM_ATENDIMENTO"}'
curl -s -X POST $BASE/tickets/1/status -H "Authorization: Bearer $AGENTE_TOKEN" \
  -H "Content-Type: application/json" -d '{"status":"RESOLVIDO"}'

# comentar com anexo (multipart)
curl -s -X POST $BASE/tickets/1/comments -H "Authorization: Bearer $TOKEN" \
  -F "body=Segue o log do erro" -F "files=@log.txt"

# timeline e métricas
curl -s $BASE/tickets/1/timeline -H "Authorization: Bearer $TOKEN"
curl -s $BASE/metrics -H "Authorization: Bearer $TOKEN"
```

## Erros

Todos os erros usam **ProblemDetail (RFC 9457)** com `title`, `status`, `detail`
e `instance`:

| Status | Quando |
|---|---|
| 401 | Sem token / credencial inválida / refresh expirado ou reusado |
| 403 | Papel insuficiente (ex.: cliente tentando transição) |
| 404 | Inexistente **ou sem acesso** (não vaza existência) |
| 409 | Transição inválida, estado errado, email/invite duplicado |
| 413 | Anexo > 10 MB |
| 415 | MIME fora da whitelist ou content-type errado da rota |
| 422 | Validação (`violations` por campo) |

## Decisões de design

- **Modular monolith** (`common`, `security`, `auth`, `user`, `ticket`, `sla`,
  `attachment`, `metrics`) — módulos conversam por serviços, não por repositórios.
- **SQLite + Spring SQL Init**: escolhido por simplicidade (zero dependências);
  `schema.sql`/`data.sql` idempotentes no lugar do Flyway (Flyway 10 não suporta
  SQLite). Migrações ficam fora de escopo por enquanto.
- **Refresh token com rotação + detecção de reuso**: apresentar um token já
  rotacionado revoga a família inteira (commit em transação própria, já que a
  exceção de 401 desfaria a transação externa).
- **404 em vez de 403** para chamados/allegados de terceiros: não vaza
  existência de recursos.
- **Sem scheduler de SLA**: violação é recalculada sob demanda (leitura e
  transições) — YAGNI; um job agendado é a evolução natural.
- **CORS** liberado para `localhost:3000/5173` (pronto para um front futuro).

## Limitações conhecidas

- Calendário sem feriados; notificações e escalonamento fora de escopo.
- Sem papel `ADMIN`: políticas de SLA e invite codes mudam via seed/SQL.
- Access token de 15 min continua válido após desativação da conta (tradeoff
  aceito sem endpoint de revogação de access tokens).
- Métricas carregam todos os os tickets em memória — suficiente para portfólio,
  não para milhões de linhas.

## Estrutura

```
src/main/java/com/helpdesk/
├── auth/        registro, login, refresh, invite codes
├── user/        perfil próprio
├── ticket/      tickets, transições, timeline (comentários + auditoria)
├── sla/         calendário útil, calculadora, políticas
├── attachment/  upload/download (10 MB, whitelist de MIME)
├── metrics/     resumo global e por agente
├── security/    JWT, filter chain
└── common/      ProblemDetail, config (OpenAPI, beans)
src/main/resources/
├── schema.sql   idempotente (SQLite)
├── data.sql     políticas de SLA + invite seed
```
