---
title: "RAG Query UI — chat con citas obligatorias"
type: design-spec
owner: pablo
phase: 3
last-verified: 2026-05-13
status: draft
related-modules:
  - knowledge
source-of-truth: "rag-query-service (gRPC) + R2R"
tags:
  - frontend
  - rag
  - chat
  - knowledge
depends-on:
  - module:knowledge
  - service:rag-query-service
  - service:rag-ingestor
related-runbooks: []
see-also:
  - ./03-runbook-viewer.md
  - ../../adrs/0003-r2r-rag-stack.md
  - ../../adrs/0004-second-brain-storage-and-knowledge-graph.md
  - ../../../ARCHITECTURE.md
---

# RAG Query UI

## User story

Como cualquier usuario autenticado, quiero hacer preguntas en lenguaje natural sobre el cluster, los servicios y los runbooks, y obtener respuestas con citas verificables — si el sistema no encuentra evidencia documentada, debe decírmelo en lugar de inventar `[source: ARCHITECTURE.md §2.4]`.

## Componentes

- **Layout principal (inspiración Perplexity + Open WebUI)**:
  - **Sidebar izquierda (260dp)**: historial de queries del usuario con search local. Cada item: primera línea de la pregunta + timestamp relativo + chip con número de fuentes citadas.
  - **Main area (flex)**: conversación scrolleable. Cada turno = pregunta del usuario (alineada a la derecha) + respuesta del asistente (alineada a la izquierda) con citation chips.
  - **Input bar (sticky abajo)**: textarea multiline con auto-resize (1-8 líneas), botón Enviar a la derecha, hint "Cmd+Enter para enviar".
- **Respuesta del asistente**:
  - Texto en streaming (token a token) con cursor parpadeante mientras llega.
  - Citation chips inline `[source: docs/services/postgres-n8n.md#deploy@a1b2c3d]` renderizados como pill clicable; click abre el [Runbook Viewer](./03-runbook-viewer.md) con deep-link al ancla.
  - **Indicador de citation validator** (header de la respuesta):
    - Icono verde checkmark + "Todas las citas resolvieron ({n})" si pasaron `CitationValidator` `[source: ARCHITECTURE.md §2.4]`.
    - Icono rojo + tooltip "{n} citas rechazadas (HTTP 422)" si alguna falló.
  - Lista plegable "Fuentes ({n})" al final de la respuesta con cada chunk citado: path + score similaridad + snippet 200 chars.
- **Bandera anti-alucinación (capa 5)**:
  - Si `top_k_score < 0.6`, en lugar de respuesta normal aparece un banner rojo grande full-width centrado: "⚠ No encuentro evidencia documentada para responder." con sugerencias de búsqueda alternativas y botón "Documentar yo mismo" (link al editor de knowledge).
- **Top bar**:
  - Selector de modo: "Búsqueda factual" / "Búsqueda exploratoria" / "Agentic multi-hop" (Fase 7) — sólo el primero activo en Fase 3.
  - Botón "Nueva conversación".

## State / Props

- **ViewModel**: `RagQueryViewModel(ragQueryClient: RagQueryGrpcClient, knowledgeClient)`.
- **Flows**:
  - `conversation: StateFlow<List<Turn>>` — pares pregunta/respuesta.
  - `streaming: StateFlow<StreamingState>` — `Idle | Sending | Streaming | Validating | Done | Failed`.
  - `validatorResult: StateFlow<ValidatorResult?>` — output del `CitationValidator`.
  - `history: StateFlow<List<QueryRecord>>` — `GET /api/v1/knowledge/queries?user=me&limit=50`.
- **Submit**:
  - Cmd+Enter o click en botón.
  - POST a `rag-query-service` (gRPC streaming, proxy REST `/api/v1/knowledge/query`).
  - El backend devuelve markdown + citations array; el frontend valida con `CitationValidator` antes de mostrar (o lo mostrará en stream y al cierre marcará las rotas).

## Layout responsive

| Breakpoint | Layout |
|---|---|
| Compact (<600dp) | Sólo main area. Sidebar historial colapsa a botón "Historial" arriba que abre bottom-sheet. Input bar full-width sticky. |
| Medium (600-840dp) | Sidebar 220dp + main flex. Sin lista de fuentes expandida por defecto. |
| Expanded (>840dp) | Sidebar 260dp + main flex con max-width 840dp centrado. Lista de fuentes desplegada por defecto. |

## Theming

- Background: `#0F1419`.
- Surface burbujas pregunta (usuario): `#212529`, border-radius asimétrico (8dp salvo esquina inferior-derecha 2dp).
- Surface burbujas respuesta: `#1A1E23`, border-radius asimétrico (8dp salvo inferior-izquierda 2dp).
- Primary citation chip: `#00E676` alpha 0.16 fondo, texto `#00E676`, borde 1dp.
- Validator success icon: `#00E676`.
- Validator error icon: `#FF5252`.
- Banner anti-alucinación: fondo `#FF5252` alpha 0.12, borde `#FF5252` 1dp, icono `⚠` grande `#FF5252`, texto blanco.
- Input bar: surface `#1A1E23`, focus ring `#00E676` 2dp.
- Cursor streaming: parpadeo a `#00E676`.
- Tipografía:
  - body-large para mensajes.
  - label-small para timestamps y chips.
  - JetBrains Mono para snippets de código en respuestas.

## Accesibilidad

- ARIA roles: `role=log` con `aria-live=polite` en el área de conversación para que el screen reader anuncie respuestas streamed.
- Input bar con `role=textbox` `aria-multiline=true` y `aria-describedby` apuntando al hint "Cmd+Enter para enviar".
- Citation chips con `aria-label="Cita {n}: {path}, sección {section}, commit {sha}"`.
- Banner anti-alucinación con `role=alert` para que se anuncie inmediatamente.
- Navegación por teclado:
  - Cmd+Enter envía.
  - Tab navega de input a citation chips de la última respuesta.
  - Flecha arriba en input vacío rellena con la última pregunta.
  - Cmd+K abre el sidebar historial (en compact, abre el bottom-sheet).
- Contraste WCAG AA verificado en citation chip y banner rojo.

## Copy literal (español)

- Placeholder input: "Pregunta sobre el cluster, servicios o runbooks…"
- Hint input: "Cmd+Enter para enviar"
- Selector modo: "Factual" / "Exploratorio" / "Multi-hop (Fase 7)"
- Botón: "Nueva conversación"
- Validator success: "Todas las citas resolvieron ({n})"
- Validator failure: "{n} citas rechazadas. Click para ver detalle."
- Banner anti-alucinación título: "No encuentro evidencia documentada"
- Banner anti-alucinación cuerpo: "Tu pregunta no coincide con ningún documento indexado por encima del umbral de confianza (0.6). Reformúlala o documenta el conocimiento que falta."
- CTA banner: "Documentar yo mismo"
- Fuentes label: "Fuentes ({n})"
- Empty conversation: "Pregunta lo que necesites. Las respuestas vienen siempre con citas verificables."
- Empty history: "Aún no tienes consultas guardadas."
- Toast send error: "No se pudo enviar la pregunta. Reintentar."

## Estados

- **Idle**: pantalla inicial con empty state + sugerencias de preguntas ("¿Cuántos pods hay en `cluster-ops`?", "¿Cómo rotar el secret de Discord?", "¿Qué runbook aplica si OOMKilled se repite?").
- **Sending**: input deshabilitado, spinner inline en el botón Enviar.
- **Streaming**: tokens llegando con cursor parpadeante. Validator icon en estado "checking".
- **Validating**: respuesta completa, validator corriendo (animación pulse en icon).
- **Done success**: validator verde, fuentes desplegadas.
- **Done partial (citas rotas)**: validator rojo, chips problemáticos con borde rojo + tooltip explicativo, resto de la respuesta visible.
- **Done low-confidence**: banner anti-alucinación reemplaza la respuesta (no se muestra texto inventado).
- **Failed (red)**: toast "No se pudo enviar la pregunta. Reintentar."
- **Failed (rag-query down)**: banner "El servicio RAG no está disponible. Revisa estado en Observability."

## Cita la fuente

- Datos vienen de: `rag-query-service` (Python R2R + facade Spring Kotlin) `[source: ARCHITECTURE.md §3]`.
- Validación: `CitationValidator` middleware Kotlin, capa 4 de defensa `[source: ARCHITECTURE.md §2.4]`.
- Umbral 0.6: capa 5 de defensa, ver `feedback_rag_anti_hallucination` `[source: MEMORY.md]` y `ADR-0003`.
- Comunicación frontend ↔ backend: gRPC streaming vía proxy REST `[source: ARCHITECTURE.md §5]`.

---

## Prompt para Claude Design

Copy-paste literal para https://claude.ai/design:

```
Estoy diseñando una UI de "Chat RAG con citas obligatorias" para un Internal
Developer Platform (IDP). Inspiración: Perplexity + Open WebUI.

Tema: dark Material 3, OLED-optimizado, primary #00E676 (neon green),
background #0F1419, surface burbujas #1A1E23 / #212529 según rol,
RoundedCornerShape(8.dp) asimétrico (esquina origen a 2dp).
Stack: Compose Multiplatform Web (target wasmJs).

Layout:
- Sidebar izquierda 260dp con historial de queries: primera línea de la
  pregunta, timestamp relativo, chip con número de fuentes citadas. Search
  local arriba.
- Main area: conversación scrolleable. Burbujas pregunta del usuario alineadas
  derecha, respuestas del asistente alineadas izquierda. Texto en streaming
  con cursor verde parpadeante.
- Input bar sticky abajo: textarea multiline auto-resize (1-8 líneas),
  placeholder "Pregunta sobre el cluster, servicios o runbooks…", hint
  "Cmd+Enter para enviar", botón Enviar circular neon.

Citation chips inline: cada [source: docs/services/postgres-n8n.md#deploy@a1b2c3d]
se renderiza como pill verde neon clicable (fondo #00E676 alpha 0.16, texto
#00E676, borde 1dp). Click abre el Runbook Viewer.

Header de cada respuesta del asistente: icono de citation validator.
- Verde checkmark + "Todas las citas resolvieron ({n})" si validan.
- Rojo + tooltip "{n} citas rechazadas" si alguna falla HTTP 422.

Al final de la respuesta: lista plegable "Fuentes ({n})" con path, score
de similaridad y snippet de 200 chars.

**Bandera anti-alucinación capa 5**: si la confianza del top-K chunks < 0.6,
en lugar de respuesta aparece un banner GIGANTE full-width:
- Fondo #FF5252 alpha 0.12, borde #FF5252.
- Icono "⚠" grande #FF5252.
- Título: "No encuentro evidencia documentada".
- Cuerpo: "Tu pregunta no coincide con ningún documento indexado por encima
  del umbral de confianza (0.6). Reformúlala o documenta el conocimiento que
  falta."
- CTA: "Documentar yo mismo".

Top bar: selector "Factual / Exploratorio / Multi-hop (Fase 7)" y botón
"Nueva conversación".

Breakpoints:
- Compact (<600dp): main area sola, sidebar historial en bottom-sheet activado
  desde botón "Historial".
- Medium (600-840dp): sidebar 220dp + main, fuentes colapsadas por defecto.
- Expanded (>840dp): sidebar 260dp + main con max-width 840dp centrado,
  fuentes expandidas.

Estados: Idle con sugerencias ("¿Cuántos pods hay en cluster-ops?"), Streaming
con cursor, Validating con pulse en icon, Done success, Done partial con
chips de borde rojo, Done low-confidence con banner anti-alucinación,
Failed con banner "El servicio RAG no está disponible."

Accesibilidad: role=log aria-live=polite en la conversación, role=alert en
el banner anti-alucinación, citation chips con aria-label completo, foco
trap correcto, contraste WCAG AA.

Copy en español neutro. Genera mockups wireframe + high-fidelity para los
3 breakpoints, incluyendo un caso con banner anti-alucinación activo.
```
