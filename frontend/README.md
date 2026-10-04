# Help Desk — Frontend

SPA React da Help Desk API. Projeto de portfólio: mesma base de código do backend
(monorrepo), com i18n (PT-BR/EN), tema claro/escuro e autorização sempre validada
pela API.

## Stack

React 19 · Vite · TypeScript (strict) · React Router · TanStack Query · Tailwind CSS 4 · Vitest + Testing Library

## Como rodar

```bash
# terminal 1 — backend (porta 8080)
mvn spring-boot:run        # se não recompilar: mvn compile antes

# terminal 2 — frontend (porta 5173, proxy /api -> 8080)
cd frontend
npm install
npm run dev
```

- Dev: http://localhost:5173 (proxy `/api` → 8080)
- Produção: `npm run build` gera os arquivos em `src/main/resources/static/`;
  o Spring serve tudo em http://localhost:8080 (rotas SPA com fallback).

```bash
npm test      # Vitest (33 testes)
npm run build # tsc + vite build
```

## Estrutura

```
src/
├── app/          # router (com guards) e providers (Query/Auth/Theme/I18n)
├── api/          # client HTTP tipado — refresh single-flight centralizado aqui
├── auth/         # auth-provider · auth-client · token-storage · guards · páginas
├── tickets/      # páginas, componentes, hooks, API e tipos do domínio
├── metrics/ sla/ profile/   # mesmo padrão por domínio
├── i18n/         # dicionários pt-BR.json / en.json + provider
├── theme/        # tema claro/escuro via data-theme
├── components/   # ui/ (badges, field, ícones autorais) e layout/ (sidebar, topbar)
└── lib/          # formatação de data/métricas
```

## Decisões que valem ler no código

- **Tokens**: access e refresh em `sessionStorage` — nunca `localStorage`.
- **Refresh**: centralizado em `api/client.ts`; 401 → 1 refresh (single-flight,
  requisições simultâneas compartilham a promise) → retry único → falhou: limpa
  sessão e dispara `auth:logout`.
- **Identidade × autorização**: o JWT decodificado só guia a UI; o backend decide
  (403 é o veredito). Os guards de rota (`auth/guards.tsx`) escondem telas, não
  substituem a API.
- **Filtros na URL**: a query string é a fonte de verdade (React Query usa os
  params na `queryKey`) — refresh preserva, voltar/avançar funciona.
- **Anexos**: upload em `FormData` com validação de UX que espelha a API
  (10 MB/arquivo, whitelist de MIME); download via `fetch` autenticado → Blob →
  `createObjectURL` (nunca URL pública).
- **Matriz de transições**: `tickets/components/ticket-transitions.ts` copia as
  regras do backend para exibir só botões válidos — e ainda assim o servidor
  valida tudo de novo.

## Testes

- `api/client.test.ts` — contrato do refresh (1 retry, single-flight, falha → logout).
- `tickets/test/transitions.test.ts` — matriz de transições por papel/status.
- `tickets/test/tickets.test.tsx` — filtros refletem na URL.
- `tickets/test/comments.test.ts` — validação de anexos e multipart.
- `metrics/`, `profile/`, `test/shell.test.tsx` — páginas, formatação, guards e preferências.
