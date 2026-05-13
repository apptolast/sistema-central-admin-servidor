---
title: "ADR-0004: Storage en git + knowledge graph en 3 capas para el segundo cerebro"
status: accepted
date: 2026-05-13
owner: pablo
supersedes: null
superseded-by: null
tags: [architecture, rag, second-brain, knowledge-graph, anti-hallucination]
---

# ADR-0004: Storage en git + knowledge graph en 3 capas para el segundo cerebro

## Status

**Accepted** — 2026-05-13.

## Context

ADR-0003 establece R2R + Spring AI como engine RAG. Este ADR responde a la pregunta complementaria: **¿dónde viven los documentos?** ¿En pgvector? ¿En un wiki tipo Outline? ¿En git? ¿En Ubicloud? ¿Cómo se construye el "árbol de grafos" que relaciona conceptos entre sí?

El owner declara dos requisitos no-negociables:

1. **Fuente de verdad debe ser auditable, versionada, diffable, propiedad del owner.** No vivir dentro de un servicio que pueda morir o cuyo schema cambie sin aviso.
2. **El sistema NUNCA debe inventar relaciones entre conceptos.** Una respuesta como "el servicio X depende de la BD Y" sólo es válida si esa relación está documentada explícitamente.

Opciones de storage evaluadas:

| Opción | Auditabilidad | Versionado | Coste RAM | Decoupling |
|--------|---|---|---|---|
| **Markdown en git** | git history + commit SHA por chunk | Total (cada cambio = commit) | 0 | Total |
| Outline self-hosted | DB Postgres propia | Solo el last-modified | ~400 MB | Medio |
| AppFlowy / AFFiNE | DB propia | Limited | ~600 MB | Medio |
| Obsidian local | Filesystem cliente | git si activas plugin | 0 server | Total (sólo cliente) |
| Ubicloud knowledge engine | N/A (Ubicloud no tiene) | N/A | 4-6 GB (instalación completa) | N/A |
| pgvector como source | NO (DB no es source-of-truth) | NO | — | — |

Ubicloud merece nota aparte: es un control plane open-source para construir **tu propio cloud público multi-tenant** sobre bare metal (alternativa a AWS). Resuelve un problema diferente, no tiene un "knowledge engine". Tomaremos prestada su filosofía control-plane/data-plane (ver §2.5 en ARCHITECTURE.md), pero NO instalamos Ubicloud.

## Decision

### Storage de documentos: Markdown en git (este repo)

Toda la documentación factual del IDP vive en `docs/` en este repositorio, con frontmatter YAML obligatorio (ver `docs/_template.md`). Estructura:

```
docs/
├── runbooks/         Procedimientos operacionales (los 27 de cluster-ops/audit/RUNBOOKS migrados + nuevos)
├── services/         Un .md por servicio del cluster (postgres-n8n.md, timescaledb.md, ...)
├── infrastructure/   Hetzner-vps.md, kubernetes.md, networking-traefik.md, cloudflare-dns.md
├── adrs/             Architecture Decision Records (inmutables)
├── _template.md      Plantilla obligatoria de frontmatter
└── _live/            (gitignored) Snapshots vivos del cluster — NO van a git
```

### Índice de búsqueda: PostgreSQL + pgvector

Tres tablas:

| Tabla | Contenido | Actualizada por |
|---|---|---|
| `doc_chunks` | `chunk_id`, `source_path`, `source_commit`, `chunk_idx`, `excerpt_text`, `embedding (vector)`, `verified_at` | `rag-ingestor` al detectar cambios en git (poll cada 5 min) |
| `doc_relations` | `(source_doc, target_entity, relation_type, declared, confidence, source_chunk_id)` | `rag-ingestor` — declarativas del frontmatter (declared=true), extraídas por R2R (declared=false) |
| `live_documents` | `doc_id`, `snapshot_ts`, `content`, `embedding` | `cluster-watcher` — overwrites cada 1h, NO va a git |

### Knowledge graph en 3 capas

#### Capa 1 — Declarativa (Fase 3, 100% determinista, cero LLM)

Cada .md declara sus relaciones explícitamente en el frontmatter YAML:

```yaml
---
title: PostgreSQL — n8n
owner: pablo
type: service
source-of-truth: kubectl -n n8n get statefulset postgres-n8n
last-verified: 2026-05-13
tags: [database, postgres, n8n]
depends-on:
  - namespace:n8n
  - pvc:postgres-n8n-longhorn
used-by:
  - service:n8n-prod
related-runbooks:
  - RB-10-pg-connections-high
---
```

El `rag-ingestor` parsea y popula `doc_relations` con `declared=true, confidence=1.0`. **Estas edges nunca pueden ser alucinadas** — son código que el humano commitea a git.

#### Capa 2 — Extraída (Fase 3, vía R2R, LLM con verificación)

R2R extrae automáticamente entidades + relaciones del cuerpo del texto cuando indexa. Para frases como "el backend invernadero-api consume timescaledb y emqx" genera edges con `declared=false, confidence=0.85` apuntando al chunk fuente. La UI muestra estas edges con badge ⚠️ "inferida" hasta que un humano las acepta vía `accept_inferred_relation()` → `declared=true`.

#### Capa 3 — Inferida multi-hop (Fase 7, vía Cognee opcional)

Cognee añade razonamiento sobre el grafo: preguntas multi-hop como "si cae timescaledb-0, ¿qué se rompe en cascada?" hacen DFS por `depends-on` recursivamente. Cada hop cita la edge usada como evidencia. Si una cadena depende de edges `declared=false`, la respuesta lo señala explícitamente.

### Cinco capas anti-alucinación (defensa en profundidad)

1. **El LLM nunca tiene los datos memorizados** — los recupera bajo demanda de chunks reales en cada query.
2. **Toda respuesta DEBE incluir citas** con formato `[source: path/file.md#section@commitsha]` (estática) o `[source: live:inventory@2026-05-13T18:00Z]` (snapshot vivo).
3. **CitationValidator** (middleware Kotlin): parsea respuestas, valida que cada cita resuelve a un `chunk_id` o `live_doc_id` real. Citas inventadas → HTTP 422 + log.
4. **Score threshold**: si los top-K chunks tienen similitud < 0.6 → respuesta literal "**no encuentro evidencia documentada sobre eso**".
5. **Tests E2E** en CI con queries verificadas. Deriva → build rojo.

## Consequences

### Positivas

- **git = audit trail completo**: cada cambio firmado, diffable, atribuible a un autor.
- **Refactor docs trivial**: `git mv`, `git rebase`, `sed -i`, todo funciona.
- **Backup nativo**: GitHub + clones locales del repo.
- **Cero dependencia de un servicio adicional** (Outline, AppFlowy, etc.).
- **Cita reproducible**: con el commit SHA en cada cita, la respuesta es reproducible aunque el doc cambie después.
- **Conscient anti-hallucination**: 5 layers documentadas y testeadas.

### Negativas

- **UX de edición inferior a Notion/Outline**: editar markdown en Compose Web no es WYSIWYG. Mitigado: en Fase 7, si la UX limita, se enchufa Outline u Obsidian como capa visual (export periódico a .md → git).
- **Coordinación humana**: cuando un servicio cambia, alguien debe actualizar el .md correspondiente. Mitigado con la rutina `docs-drift-detector` (Fase 6) que detecta drift entre estado real y docs.
- **Capa 2 (extraídas) requiere humano para aceptar**: introduce un workflow de review. Mitigado con UI clara y batch-accept para edges de alta confianza.

### Neutrales

- pgvector vive en la misma instancia Postgres que el resto del IDP. Si Postgres muere, se pierde el índice pero NO los docs (siguen en git). Re-indexación toma ~5 min.

## Alternatives considered

1. **Outline self-hosted como source-of-truth** — descartado: pierde el git history como audit trail. Considerado para Fase 7 como capa de UX sobre git.
2. **AppFlowy / AFFiNE** — descartado: menos maduro en mayo 2026, dependencia de roadmap externo.
3. **Markdown en pgvector directamente (sin git)** — descartado: la DB NO es source-of-truth (regla del proyecto), no hay audit trail, no hay diffs revisables.
4. **Confluence / Notion SaaS** — descartado: vendor lock-in, datos fuera del control del owner, coste recurrente.
5. **Ubicloud** — descartado: resuelve un problema completamente diferente (cloud público multi-tenant). Tomamos prestada su filosofía, no su código.
6. **Capa 2 deshabilitada (sólo declarativa)** — descartado: pierde 60-80% del valor del knowledge graph; las relaciones declarativas son insuficientes para preguntas no anticipadas.

## References

- ADR-0001 (Spring Modulith — habla del módulo `knowledge`)
- ADR-0003 (R2R + Spring AI — describe el engine que indexa estos .md)
- `docs/_template.md` — plantilla obligatoria de frontmatter
- [pgvector documentation](https://github.com/pgvector/pgvector)
- [Cognee GraphRAG (Fase 7 opcional)](https://www.cognee.ai/)
- [Markdown frontmatter spec (YAML)](https://jekyllrb.com/docs/front-matter/)

## Reversal triggers

Re-evaluar este ADR si:

- Volumen de docs supera 10K archivos (improbable a corto plazo) y git operations se vuelven lentas.
- El owner cambia el sello "audit-first" por preferir UX colaborativa síncrona (entonces Outline).
- pgvector queda obsoleto o emerge un vector store dramáticamente superior (Qdrant 2.0+, Weaviate, etc.).
