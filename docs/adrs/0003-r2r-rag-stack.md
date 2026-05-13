---
title: "ADR-0003: R2R (SciPhi-AI) + Spring AI 1.1 como stack RAG del segundo cerebro"
status: accepted
date: 2026-05-13
owner: pablo
supersedes: null
superseded-by: null
tags: [architecture, rag, ai, second-brain]
---

# ADR-0003: R2R + Spring AI 1.1 como stack RAG del segundo cerebro

## Status

**Accepted** — 2026-05-13.

## Context

El IDP necesita un **segundo cerebro** que responda preguntas factuales sobre la infraestructura ("¿qué PostgreSQL hay desplegados?", "¿qué pasa si cae timescaledb-0?", "¿cuál es el owner del cluster-watcher?") **sin alucinar**. El owner (Pablo) declara explícitamente como riesgo inaceptable que el sistema invente datos.

Opciones reales en mayo 2026:

| Solución | Citation accuracy | Anti-hallucination | Spring integration | Kubernetes deploy | Status |
|---|---|---|---|---|---|
| **R2R (SciPhi-AI)** | 75-80% con Deep Research API | Hybrid search + source tracking | REST API → fácil cliente Kotlin | Docker compose / K8s manifests | GA, activo 2026 |
| Cognee | 82%+ con GraphRAG | Graph + chain-of-thought, 20-35% mejor grounding | Python SDK + REST wrapper | Docker | $7.5M funded, 70+ deploys producción |
| Onyx (ex Danswer) | 70% | Hybrid + 40+ connectors | REST API | K8s ready | Activo, MIT |
| Spring AI 1.1 solo (custom RAG) | Variable, depende de implementation | Manual | Nativo Spring | Spring Boot | GA, refleja patrón LangChain4j |
| LangChain4j + custom | Variable | Manual | Spring Boot via integrations | Spring Boot | GA 1.3+ |

Constrains operacionales:

- Embeddings via OpenAI API (mejor calidad disponible) o Ollama local (privacidad, pero peor calidad + más RAM).
- pgvector ya disponible en el stack (n8n usa Postgres; añadir extensión es trivial).
- Markdown en git como fuente de verdad (no negociable, ver ADR-0004).

## Decision

Adoptar **stack RAG en dos capas**:

### Capa "engine" — R2R (SciPhi-AI) 3.x

- Corre como microservicio Python en su propio pod (`rag-query-service/python-r2r/`).
- Hace ingestion + chunking + embedding + retrieval + generation.
- Expone REST API en `:7272`.
- **NO se expone directamente al frontend**. Lo envuelve el facade Kotlin.

### Capa "facade" — Spring Boot Kotlin con Spring AI 1.1

- Microservicio extraído `rag-query-service/kotlin-facade/`.
- Expone REST público al frontend / a otros módulos: `POST /api/knowledge/ask`.
- Llama internamente a R2R via REST.
- **CitationValidator middleware**: parsea cada respuesta, valida que cada cita resuelve a un `chunk_id` real en pgvector. Citas inventadas → HTTP 422.
- Aplica el threshold de score < 0.6 → "no encuentro evidencia documentada".
- Loguea todas las queries + respuestas + citas usadas para audit.

### Embeddings y vector store

- **Vector store**: PostgreSQL 16 + pgvector 0.8 (la misma instancia que el resto del IDP). Esquema `rag_chunks`.
- **Embedding model**: OpenAI `text-embedding-3-small` (1536 dims) inicialmente. Migración a Ollama local evaluada en Fase 7 si coste / privacidad lo justifica.

### Capa opcional Fase 7 — Cognee

- Sólo cuando se necesite razonamiento multi-hop sobre el knowledge graph.
- No es parte del MVP. Decisión diferida.

## Consequences

### Positivas

- **Anti-alucinación by design**: R2R retorna chunks con `source_id` + `score`; el validator garantiza que sólo se usan citas reales.
- **Spring AI 1.1 abstrae** el cliente del embedding provider — cambiar OpenAI por Ollama o Bedrock es trivial.
- **Facade Kotlin preserva el sello full Kotlin** del IDP frente a R2R Python — el frontend nunca ve Python.
- **Stack ya validado**: R2R tiene 1M+ pipelines mensuales en producción (2026), Spring AI es producto oficial Spring Project.
- **pgvector reutiliza la BD** del IDP — no añadimos otro motor (Qdrant, Weaviate, Pinecone).

### Negativas

- **Runtime Python en el cluster** — rompe el sello "todo Kotlin". Mitigación: el facade Kotlin lo envuelve; R2R es un implementation detail no expuesto al usuario.
- **Citation accuracy ~75-80%** aún deja ~20% de respuestas sin citas perfectas. Mitigado con los 5 layers de defensa anti-alucinación (ver ADR-0004).
- **Coste OpenAI API**: embeddings ~$0.02 / 1M tokens. Para ~32K chunks iniciales (~$2-5 inicial, ~$0.50 / mes reindexing).

### Neutrales

- R2R recibe actualizaciones frecuentes (proyecto joven, version 3.x); requiere monitorear releases.

## Alternatives considered

1. **Cognee únicamente** — descartado para MVP: más complejo, Python SDK como primary interface, mejor para Fase 7 como complemento.
2. **Spring AI 1.1 + custom RAG (sin R2R)** — descartado: reinventar el chunking + retrieval pipeline que R2R ya hace mejor. Spring AI sí lo usamos en el facade para abstracción del provider.
3. **LangChain4j** — descartado: cubre lo mismo que Spring AI pero con menos integración nativa con Spring Boot ecosystem.
4. **Onyx (ex Danswer)** — descartado: orientado a enterprise search con 40+ connectors que no necesitamos.
5. **AnythingLLM** — descartado: orientado a chat sobre documentos, menos extensible para nuestros patterns de citación estricta.
6. **Anthropic Claude API + custom pipeline** — descartado: lock-in al proveedor, Spring AI ofrece abstracción provider-agnóstica.

## References

- [R2R (SciPhi-AI) GitHub](https://github.com/SciPhi-AI/R2R)
- [R2R Deep Research API documentation](https://r2r-docs.sciphi.ai/)
- [Spring AI 1.1 reference documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring AI pgvector integration](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [pgvector extension](https://github.com/pgvector/pgvector)
- [Cognee GraphRAG approach](https://www.cognee.ai/blog/deep-dives/cognee-graphrag-supercharging-search-with-knowledge-graphs-and-vector-magic)
- ADR-0004 (storage + knowledge graph) — describe la capa superior

## Reversal triggers

Re-evaluar este ADR si:

- R2R cambia drásticamente su API public sin migration path (improbable en software 2026 GA).
- Coste OpenAI embeddings supera $50/mes (mover a Ollama local).
- Citation accuracy medida en producción < 70% sostenido (cambiar a Cognee como engine principal).
