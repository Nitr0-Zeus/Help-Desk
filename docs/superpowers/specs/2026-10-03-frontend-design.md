# Help Desk Frontend — Design Spec

**Data:** 2026-10-03
**Status:** aprovado pelo usuário (design + 7 correções pontuais aplicadas)
**Intenção:** SPA para interagir com a Help Desk API existente, sem alterar contratos do backend.

---

## 1. Contexto e escopo

Frontend completo da API de help desk (repo monorepo: `frontend/` dentro de `helpdesk-api`). Cobre: auth (cliente + agente com invite), lista/detalhe/criação de chamados, transições, timeline, comentários com anexos, métricas, políticas de SLA e perfil — com i18n (PT-BR/EN) e tema claro/escuro.

**Fora de escopo:** backend novo, gráficos com libs, SSR/Next, testes E2E pesados, publicação/deploy.

## 2. Stack

React 18 + Vite + TypeScript (strict), React Router, TanStack Query, Tailwind CSS. Testes: Vitest + Testing Library (pontos críticos: refresh single-flight, guards, transições). **Sem outras libs** (barras de métricas em CSS puro).

## 3. Decisões de design (aprovadas)

1. **Tokens:** access → `sessionStorage`; refresh → `sessionStorage`; **nunca `localStorage`**.
2. **Refresh:** centralizado no `api/client`:
   `401 → refresh → retry 1× → sucesso devolve / falha limpa sessão`.
   **Single-flight:** promise de refresh compartilhada — N requisições simultâneas com 401 disparam 1 único `/refresh`; nunca múltiplos refresh ou loops no mesmo ciclo.
3. **Logout:** `POST /api/auth/logout` → limpar tokens **independentemente do resultado** → invalidar queries → redirect `/login`.
4. **Identidade × autorização:** JWT decodificado serve para exibição/sessão/UX. Autorização real é do backend (403). A UI só esconde ações.
5. **Registro:** frontend apresenta o fluxo e envia `role` (obrigatório no contrato); caminho `AGENTE` aparece apenas com invite. Backend valida `role` + `inviteCode`. Seleção do usuário nunca é confiança.
6. **Guards:** `PublicRoute` (login/register), `ProtectedRoute` (rotas da app), `RequireRole` (definido e testado; aplicado somente onde o backend exige papel — hoje nenhuma rota da lista exige).
7. **Filtros:** a **URL é a fonte de estado** (`/tickets?status=…&priority=…&page=…&size=…`); params entram na `queryKey` do React Query; refresh da página preserva; compartilhável; voltar/avançar funciona.
8. **Anexos:** upload via `FormData`; validação de UX **espelhando limites efetivos da API** — 10 MB **por arquivo** (`FileStorage.MAX_SIZE`), MIME whitelist `image/png, image/jpeg, image/gif, application/pdf, text/plain, application/zip` (backend `max-file-size: 10MB`, `max-request-size: 60MB`); backend permanece autoridade. Download: `fetch` com header de auth → `Blob` → `URL.createObjectURL` (sem URLs públicas).
9. **i18n:** dicionários `pt-BR`/`en` + hook `useT`, switcher no topbar.
10. **Tema:** claro/escuro via `data-theme` + CSS variables; default `prefers-color-scheme`; preferências (não tokens) em `localStorage`.
11. **URLs de anexos/remoção:** não existe endpoint de remover anexo individual. `DELETE /api/tickets/{id}` pertence ao domínio do chamado (dono + status `ABERTO`), validação de UX no detalhe (Task 6).
12. **Backend:** única mudança aprovada = `SpaForwardConfig` (forward de GET não-API → `/index.html`).

## 4. Integração

- **Dev:** Vite na 5173, proxy `/api` → `localhost:8080`.
- **Prod:** `npm run build` → `src/main/resources/static/` (gitignorado), Spring serve na 8080 (mesma origem).
- Contrato da API: fonte = README/OpenAPI do backend. Nada de endpoints/tipos/regras novos inventados.

### Contrato resumido

- Auth: `POST /api/auth/{register,login,refresh,logout}` → `AuthResponse {accessToken, tokenType, expiresIn, refreshToken, refreshExpiresAt}`; register `{name,email,password,role,inviteCode?}`.
- Perfil: `GET/PATCH/DELETE /api/users/me`, `POST /api/users/me/password`.
- Tickets: `GET/POST /api/tickets` (filtros `status,priority,slaBreached,q,page,size,sort` → página `{content,page,size,totalElements,totalPages}`), `GET/PATCH/DELETE /api/tickets/{id}`, `POST /{id}/status {status}`, `POST/DELETE /{id}/assign`, `GET /{id}/timeline`, `POST /{id}/comments` (multipart: `body` + `files[]`).
- `GET /api/attachments/{id}` (auth), `GET /api/sla-policies`, `GET /api/metrics`, `GET /api/metrics/agents[/{id}]`.
- Erros: ProblemDetail RFC 9457 — 401/403/404/409/413/415/422 (`violations`).

## 5. Estrutura

```
frontend/src/
├── app/           # router, providers (Query/Auth/Theme/I18n)
├── api/           # client HTTP tipado (refresh centralizado), tipos dos DTOs
├── auth/          # auth-provider.tsx auth-client.ts token-storage.ts auth-types.ts guards.tsx pages/
├── tickets/       # pages/ components/ hooks/ api/ types.ts
├── metrics/ sla/ profile/   # mesmo padrão por domínio
├── i18n/          # pt-BR.json en.json useT
└── components/    # ui/ e layout/ — somente compartilhados
```

## 6. Direção visual

Painel operacional clean (Zendesk/Jira-like): sidebar/topbar, cards e tabelas densas, badges semânticos — prioridade `BAIXA` cinza, `MEDIA` azul, `ALTA` laranja, `CRITICA` vermelho; status `ABERTO` cinza, `EM_ATENDIMENTO` azul, `AGUARDANDO_CLIENTE` âmbar, `RESOLVIDO` verde, `FECHADO` escuro; chip vermelho "SLA estourado". Tema claro/escuro. Durante a implementação: skills `impeccable` + `design-taste-frontend`; validação final com subagente `visual-qa` (Playwright), que **não altera arquitetura/comportamento sem passar pelas decisões acima**.

## 7. Testes

- `api/client`: 401 → 1 refresh → 1 retry; N-401 simultâneos = 1 refresh (single-flight); falha de refresh → limpa sessão.
- `guards`: PublicRoute/ProtectedRoute redirecionam corretamente.
- `tickets`: lógica de quais transições exibir (matriz × papel).
- Formatação/i18n e validação de anexos (limites da API).
- QA visual no Task 11; `npm run build` + `npm test` + `mvn verify` verdes na entrega.
