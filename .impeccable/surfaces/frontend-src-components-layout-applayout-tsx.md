---
version: 1
slug: "frontend-src-components-layout-applayout-tsx"
primary_target: "frontend/src/components/layout/AppLayout.tsx"
related_targets: []
---

# Surface brief — Help Desk App Shell

Primary target: `frontend/src/components/layout/AppLayout.tsx`
Mode: Operate (app UI autenticada — tarefas de suporte em fluxo)

## Scope and job

Shell autenticado (sidebar + topbar + área de conteúdo) que serve todas as rotas da
app: tickets, detalhe, métricas, SLA, perfil. Job: cliente/agente enxergar a fila,
o estado de SLA e agir sem atrito. Audience: usuários do sistema (clientes e
agentes) em repetição diária; recrutador em avaliação. Proof: fluxo real sobre a
API (badges de status/prioridade/SLA vivos na tabela). Constraints: backend
intocado, i18n PT-BR/EN (default PT-BR), tema claro/escuro, sem libs novas,
system stack de fontes.

## Chosen direction and memorable moment

Direção pinned pelo brief: **painel operacional clean** (Zendesk/Jira-like,
Restained: neutros slate + azul de ação). Momento memorável: o **chip de SLA**
vermelho "SLA estourado" / âmbar "aguardando cliente" na fila — o tempo do
chamado lido antes do texto.

## Unresolved decisions

Nenhuma estrutural; refinamentos de spacing/badge caem no finish review.

## Direction contract

### THESIS

O instrumento onde o SLA é protagonista visível: a fila de chamados deixa o
estado do tempo (no prazo, pausado, estourado) legível antes do texto do título.
Recusa o arranjo default da categoria: hero-métrico (número gigante + label) e
grade de cards iguais de ícone+título+texto como estrutura de página.

### OWN-WORLD

Restrained: fundo slate-50 (dark: slate-900), superfície branca (dark: slate-800),
bordas 1px slate-200 (dark: slate-700), texto slate-900 (dark: slate-100), neutro
secundário slate-500 para sidebar/toolbar, azul de ação único `#2563eb`
(dark `#60a5fa`) em ação primária/seleção atual. Vocabulário semântico de estado
só para significado: âmbar=aguardando cliente, verde=resolvido, vermelho=
crítico/SLA estourado, azul=em atendimento, cinza=aberto/fechado. Radius 6px,
sombras com offset+blur suaves, sem halos. Uma família sans (system stack),
escala fixa 1.125–1.2, labels em peso 500. Badges compactos: pílula com ponto
colorido. Tema claro default (cena: escritório com luz do dia), dark por
preferência persistida.

### STORY

Em segundos o visitante sabe qual é a fila, o que estoura SLA e o que fazer:
cliente acompanha e responde; agente assume, transiciona e fecha. Sai com o
chamado movido, não só admirando um dashboard.

### FIRST VIEWPORT

Desktop: sidebar fixa 240px à esquerda (marca "Help Desk" no topo, nav — Chamados,
Métricas, SLA, Perfil — com item atual em azul, rodapé da sidebar com menu do
usuário, switcher PT-BR/EN e toggle de tema); topbar com título da rota à
esquerda e ação primária contextual à direita (ex.: "Novo chamado"); conteúdo:
barra de filtros em linha única (status, prioridade, SLA, busca) e, abaixo,
tabela densa — protocolo, título, badge de status, badge de prioridade, chip de
SLA, data —, linhas com hover e click abre o detalhe. Mobile (<768px): sidebar vira
drawer acessível por botão no topbar; tabela reduz para cartões de linha.

### FORM

Form escolhido: painel operacional clean (posição 1 da lista ordenada — também a
direção fixada no brief do usuário; o roll não correu por ser direção pinned e
build code-led). Seed key: n/a (sem concept-seed: request preciso + direção
pinned).

### FINISH

unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
