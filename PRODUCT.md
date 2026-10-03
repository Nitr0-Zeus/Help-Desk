# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Clientes** (usuários finais do sistema): abrem chamados, acompanham a timeline, respondem quando o chamado está aguardando, anexam arquivos.
- **Agentes de suporte**: atendem chamados (atribuição, transições de status, comentários), acompanham SLA e métricas.
- **Autor do projeto** (portfólio): público secundário — recrutador/avaliador lendo o código e a UI como evidência de skill full-stack.

## Product Purpose

Interface operacional da Help Desk API (backend existente): permitir que clientes e agentes gerenciem chamados técnicos com SLA real (horas úteis, pausa, violação) de ponta a ponta. Sucesso = fluxo completo (login → criar → atender → comentar com anexo → fechar → métricas) funcionando sem curl, com a API como única fonte de verdade.

## Positioning

UI completa e plural (i18n PT-BR/EN, tema claro/escuro) sobre uma API de help desk com SLA de verdade — não um CRUD decorado: pausa de SLA em aguardando-cliente, auditoria na timeline e métricas por agente são a diferença visível.

## Operating Context

- Desenvolvimento: Vite (5173) com proxy `/api` → Spring Boot (8080); produção: build servido pelo próprio Spring na 8080.
- Dados reais de demonstração: usuários demo (`cliente@demo.com`, `agente@demo.com`, senha `Senha@123`) e invite `AGENTE-2026` semeados pelo backend.
- Dois modos de sessão: cliente (visão restrita aos próprios chamados) e agente (visão total).

## Capabilities and Constraints

- Cobre auth (login/registro com invite), lista/detalhe/criação de chamados, transições, atribuição, timeline, comentários com anexos, métricas, políticas de SLA e perfil.
- **Backend intocado**: nenhum endpoint/regra novo; única mudança de infra aprovada (SPA fallback) já implementada.
- i18n: PT-BR (default) e EN, com switcher; tema claro/escuro com persistência local.
- Tokens de sessão em `sessionStorage` (nunca `localStorage`); autorização real sempre no backend.
- Stack: React 19 + Vite + TypeScript + React Router + TanStack Query + Tailwind + Vitest (decidido e aprovado; sem libs extras).
- Filtros de listagem vivem na URL (estado reproduzível).

## Evidence on Hand

- Backend completo com 82 testes, README com contratos e curl, Swagger publicado.
- Spec e plano do frontend em `docs/superpowers/{specs,plans}/` com todas as decisões aprovadas.
- Não há: logo, dados de produção, nem relatórios de uso (não fabricar).

## Product Principles

1. A API é a autoridade — a UI só reflete e facilita.
2. Nenhuma mudança de contrato backend para agradar o front.
3. Simplicidade e tipagem antes de abstração.
4. O fluxo de SLA deve ser visível e compreensível na timeline.
5. Funciona em PT-BR e EN sem código duplicado.
