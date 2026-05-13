---
title: "Runbook Viewer — vista 3 columnas de los 27 runbooks"
type: design-spec
owner: pablo
phase: 3
last-verified: 2026-05-13
status: draft
related-modules:
  - knowledge
  - inventory
source-of-truth: "/home/admin/cluster-ops/audit/RUNBOOKS/"
tags:
  - frontend
  - knowledge
  - runbooks
depends-on:
  - module:knowledge
  - service:rag-query-service
related-runbooks: []
see-also:
  - ./04-rag-query-ui.md
  - ../../adrs/0003-r2r-rag-stack.md
  - ../../adrs/0004-second-brain-storage-and-knowledge-graph.md
  - ../../../ARCHITECTURE.md
---

# Runbook Viewer

## User story

Como operator, quiero navegar los 27 runbooks de cluster-ops `[source: reference_cluster_ops_audit]` con una vista tipo Notion (3 columnas: árbol, lectura, contexto) para que durante un incidente pueda llegar al procedimiento correcto en menos de 10 segundos sin abrir el repo de cluster-ops.

## Componentes

- **Columna 1 — Árbol (220dp, sticky)**:
  - Header con icono libro + título "Runbooks" + contador "(27)".
  - Campo búsqueda local "Filtrar runbooks…".
  - Árbol agrupado por categoría:
    - **Host** (RB-host-01 … RB-host-N)
    - **Kubernetes** (RB-12-pod-crashloopbackoff, RB-20-OOMKILLED-REPEAT, …)
    - **PostgreSQL** (RB-pg-…)
    - **EMQX** (`EMQX_PVC_HIGH`, `EMQX_PVC_EXPANSION_PLAN`)
    - **WireGuard** (RB-wg-…)
    - **Tier0** (`TIER0_AUTO_UPDATERS`)
    - **Observability** (`DISCORD_WEBHOOK_SECRET`)
    - **Longhorn** (`LONGHORN_BACKUP_TARGET`)
    - **TimescaleDB** (`TIMESCALEDB_INVERNADEROS_RETENTION_1Y`)
  - Cada nodo del árbol muestra: icono categoría, ID/slug del runbook, chip mini con "fresh" / "stale" (>90d sin verificar).
  - Item seleccionado: fondo `#00E676` con alpha 0.12 + barra izquierda neon.
- **Columna 2 — Lectura (flex, scroll vertical)**:
  - Markdown render con tipografía optimizada para lectura.
  - Headings H1/H2/H3 anclados (anchor link al hover).
  - Bloques de código con copy button + syntax highlight (kotlin, bash, yaml).
  - **Citation chips inline**: cada `[source: docs/services/postgres-n8n.md#deploy@a1b2c3d]` se renderiza como chip neon clicable con tooltip mostrando el commit sha; click navega al doc fuente con scroll a la sección `[source: ARCHITECTURE.md §2.4]`.
  - Breadcrumb arriba: `Runbooks › <categoría> › <nombre>`.
- **Columna 3 — Contexto (320dp, sticky)**:
  - **Sección "Afecta a"**: lista de servicios/pods/cronjobs referenciados por el runbook (declarados en frontmatter `depends-on` / `used-by`). Cada item con icono de tipo (pod / cronjob / pvc / ingress) y link a su pantalla correspondiente.
  - **Sección "Última verificación"**: chip con fecha `last-verified` y estado:
    - verde si < 30 días,
    - ámbar si 30-90 días,
    - rojo "stale" si > 90 días `[source: docs/_template.md last-verified semantics]`.
  - **Sección "Runbooks relacionados"**: enlaces a `related-runbooks` del frontmatter.
  - **Sección "Disparar runbook"**: botón "Abrir en chat RAG" (lleva al [RAG Query UI](./04-rag-query-ui.md) preseteado con "Ejecutar {nombre}").

## Lista completa de runbooks (27)

Tomada de `/home/admin/cluster-ops/audit/RUNBOOKS/` `[source: reference_cluster_ops_audit]`. Los 27 estándar (RB-01 a RB-27) más los 6 con nombre explícito:

- RB-01 a RB-27 — runbooks numerados (host, k8s, pg, wg, observability).
- `LONGHORN_BACKUP_TARGET`
- `EMQX_PVC_HIGH`
- `TIER0_AUTO_UPDATERS`
- `TIMESCALEDB_INVERNADEROS_RETENTION_1Y`
- `EMQX_PVC_EXPANSION_PLAN`
- `DISCORD_WEBHOOK_SECRET`

## State / Props

- **ViewModel**: `RunbookViewerViewModel(knowledgeClient, ragClient)`.
- **Flows**:
  - `runbooks: StateFlow<List<RunbookSummary>>` — `GET /api/v1/knowledge/runbooks`.
  - `selected: StateFlow<RunbookContent?>` — `GET /api/v1/knowledge/runbooks/{id}` (markdown + citations resueltas).
  - `related: StateFlow<List<EntityRef>>` — derivado del frontmatter del runbook seleccionado.
- **Deep link**: `/runbooks/<slug>` permite compartir el runbook actual.

## Layout responsive

| Breakpoint | Layout |
|---|---|
| Compact (<600dp) | Sólo columna 2 visible. Columna 1 entra como bottom-sheet con tab "Lista". Columna 3 como segundo tab "Contexto". |
| Medium (600-840dp) | Columnas 1 + 2. Columna 3 colapsa a botón flotante "Contexto" que abre un side-sheet de 280dp. |
| Expanded (>840dp) | Las 3 columnas visibles: 220dp + flex + 320dp. |

## Theming

- Background página: `#0F1419`.
- Surfaces columnas: `#1A1E23` con divider `#2A2F35` entre ellas.
- Primary acentos (item seleccionado, citation chips, links): `#00E676`.
- Citation chip: fondo `#00E676` alpha 0.16, texto `#00E676`, borde 1dp `#00E676` alpha 0.4, `RoundedCornerShape(8.dp)`.
- Chip stale: fondo `#FF5252` alpha 0.16, texto `#FF5252`.
- Chip warn (30-90d): fondo `#FFB300` alpha 0.16, texto `#FFB300`.
- Markdown:
  - H1: title-large, color `#FFFFFF`.
  - H2: title-medium, color `#00E676`.
  - H3: title-small.
  - body: body-large, line-height 1.6.
  - code inline: `JetBrains Mono`, fondo `#212529`, padding 2dp.
  - bloques code: fondo `#0A0D10`, borde sutil `#2A2F35`.

## Accesibilidad

- ARIA roles: `role=tree` en columna 1 con `aria-expanded` por categoría. `role=article` en columna 2. `role=complementary` en columna 3.
- Navegación por teclado:
  - Flechas arriba/abajo en árbol.
  - Flechas izquierda/derecha colapsan/expanden categoría.
  - Enter selecciona runbook y mueve foco a columna 2.
  - `/` enfoca buscador de runbooks.
  - En columna 2: Tab navega entre citation chips.
- Citation chips con `aria-label="Cita: {path}#{section}, commit {sha}"`.
- Contraste WCAG AA verificado para citation chip (`#00E676` sobre `#00E676` alpha 0.16 fondo, texto compensado).

## Copy literal (español)

- Header árbol: "Runbooks"
- Placeholder búsqueda: "Filtrar runbooks…"
- Chip stale: "Sin verificar > 90 días"
- Chip fresh: "Verificado"
- Header contexto sección 1: "Afecta a"
- Header contexto sección 2: "Última verificación"
- Header contexto sección 3: "Runbooks relacionados"
- CTA: "Abrir en chat RAG"
- Empty árbol filtrado: "Ningún runbook coincide con el filtro."
- Empty selección: "Selecciona un runbook de la izquierda para empezar."
- Empty related: "Sin runbooks relacionados declarados."
- Toast copy code: "Bloque de código copiado."

## Estados

- **Loading lista**: skeleton de 8 items en árbol.
- **Loading contenido**: skeleton de párrafos en columna 2.
- **Error contenido**: "No se pudo cargar el runbook. Puede haber sido movido. Reintentar."
- **Empty (sin selección)**: ilustración minimal con texto guía en columna 2.
- **Stale warning** (>90d): banner amarillo arriba del markdown "Este runbook no se verifica desde hace {n} días. Su contenido puede no reflejar la realidad."
- **Citation validator failure**: si alguna cita del runbook no resuelve, se renderiza el chip con borde rojo y tooltip "Cita rota: el commit/archivo ya no existe."

## Cita la fuente

- Datos vienen de: módulo `knowledge` (`GET /api/v1/knowledge/runbooks`, write-through git) `[source: ARCHITECTURE.md §3]`.
- Citation chips y validator: `CitationValidator` middleware `[source: ARCHITECTURE.md §2.4]`.
- Decisión almacenamiento: `ADR-0004` (storage + knowledge graph del segundo cerebro).
- Localización física actual: `/home/admin/cluster-ops/audit/RUNBOOKS/`, migración a `docs/runbooks/` planificada Fase 3 `[source: reference_cluster_ops_audit]`.

---

## Prompt para Claude Design

Copy-paste literal para https://claude.ai/design:

```
Estoy diseñando el "Runbook Viewer" para un Internal Developer Platform (IDP).
Inspiración: Notion 3 columnas (árbol, lectura, contexto).

Tema: dark Material 3, OLED-optimizado, primary #00E676 (neon green),
background #0F1419, surface columnas #1A1E23, divider #2A2F35,
RoundedCornerShape(8.dp). Stack: Compose Multiplatform Web (target wasmJs).

Layout de 3 columnas en expanded:
- Columna 1 (220dp, sticky): árbol de 27 runbooks agrupados por categoría
  (Host, Kubernetes, PostgreSQL, EMQX, WireGuard, Tier0, Observability,
  Longhorn, TimescaleDB). Categorías colapsables. Cada runbook con icono,
  slug (p. ej. RB-20-OOMKILLED-REPEAT, LONGHORN_BACKUP_TARGET,
  TIMESCALEDB_INVERNADEROS_RETENTION_1Y, EMQX_PVC_EXPANSION_PLAN,
  DISCORD_WEBHOOK_SECRET) y mini-chip "fresh" verde o "stale" rojo
  (>90 días sin verificar). Search "Filtrar runbooks…" arriba.
  Item seleccionado: fondo #00E676 alpha 0.12 + barra lateral neon.
- Columna 2 (flex, scroll): markdown render con tipografía cuidada (H1
  blanco, H2 verde neon, body 1.6 line-height), code blocks fondo #0A0D10
  con copy button. Breadcrumb arriba "Runbooks › Categoría › Nombre".
  **Citation chips inline**: cada [source: path#section@sha] se renderiza
  como chip verde neon clicable con tooltip del sha; click navega al doc
  fuente con scroll al ancla.
- Columna 3 (320dp, sticky): "Afecta a" (servicios/pods/cronjobs declarados
  en frontmatter, con iconos y links), "Última verificación" (chip fecha +
  semáforo verde/ámbar/rojo según <30d, 30-90d, >90d), "Runbooks
  relacionados" (links), botón "Abrir en chat RAG".

Breakpoints:
- Compact (<600dp): sólo columna 2. Columnas 1 y 3 en bottom-sheet con tabs
  "Lista" / "Contexto".
- Medium (600-840dp): columnas 1 + 2; columna 3 colapsa a botón flotante
  "Contexto" que abre side-sheet 280dp.
- Expanded (>840dp): las 3 columnas (220 + flex + 320).

Estados especiales: banner amarillo "Este runbook no se verifica desde
hace {n} días. Su contenido puede no reflejar la realidad." si stale.
Citation chip con borde rojo si la cita no resuelve.

Accesibilidad: role=tree con aria-expanded en columna 1, role=article en
columna 2, role=complementary en columna 3. Navegación por teclado completa.

Copy en español neutro. Genera mockups wireframe + high-fidelity para los
3 breakpoints, mostrando un runbook seleccionado (p. ej.
RB-20-OOMKILLED-REPEAT) con citations chips visibles.
```
