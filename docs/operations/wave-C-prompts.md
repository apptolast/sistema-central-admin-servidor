---
title: "Wave C — Phase 3 Knowledge/RAG prompts"
type: policy
owner: pablo
last-verified: 2026-05-13
audience: [phase-orchestrator-routine, oncall]
applies-to-wave: C (Phase 3 — Knowledge/RAG)
precondition:
  - wave-B-merged (skeletons + ADR-0006)
  - rag-ingestor real embeddings landed (commit 6a4bb03)
  - rag-query service landed (commit f1bd858)
---

# Wave C — Phase 3 Knowledge/RAG prompts

Copy-paste para `phase-orchestrator` routine o sesión Claude Code manual.

Cada prompt es **autocontenido** — no asume historial de sesión. Si el routine
no soporta Agent Teams aún, ejecutar los prompts secuencialmente como
subagent Tasks.

## C1 — Wire RAG ingestor como CronJob k8s

**Prompt al backend-dev / devops teammate:**

```
Crea el helm chart k8s/helm/rag-ingestor/ (Chart.yaml + values.yaml +
templates/_helpers.tpl + templates/cronjob.yaml + templates/serviceaccount.yaml).

REQUISITOS:
- CronJob (NO Deployment) — schedule "*/15 * * * *" para polling 15min
- imagen: ghcr.io/apptolast/rag-ingestor:0.1.0-SNAPSHOT
- ENV obligatorias: REPO_URL, REPO_BRANCH, DB_URL, DB_USER (Secret),
  DB_PASSWORD (Secret), OPENAI_API_KEY (Secret)
- volumeClaim ephemeral 5Gi para workdir clonado del repo
- ServiceAccount sin RBAC adicional (pod sólo necesita acceso a DB + GitHub)
- securityContext: runAsNonRoot, allowPrivilegeEscalation=false, capabilities drop ALL
- helm lint ok

Crear también docs/services/rag-ingestor.md con frontmatter (last-verified,
source-of-truth, audience) y citaciones de:
- services/rag-ingestor/src/main/kotlin/com/apptolast/ragingestor/git/GitRepoPoller.kt
- services/rag-ingestor/src/main/resources/db/migration/V1__rag_init.sql

Tests: helm lint, kubectl apply --dry-run=client. NO desplegar.
```

## C2 — Knowledge module wire al monolito

**Prompt al backend-dev teammate (owner: platform/knowledge):**

```
El módulo platform/knowledge ya tiene skeleton. Añade:

1. application/port/inbound/QueryKnowledgePort.kt — interface con un solo
   método: fun query(question: String, topK: Int = 5): List<Citation>

2. application/service/RemoteKnowledgeQueryService.kt — implementa
   QueryKnowledgePort haciendo HTTP POST a rag-query.platform.svc:8082
   con RestClient. Si timeout o 503, devuelve emptyList (no propagar excepción —
   regla anti-hallucination: mejor sin respuesta que respuesta alucinada).

3. infrastructure/RestKnowledgeClient.kt — RestClient configurado con baseUrl
   desde KnowledgeProperties (rag.knowledge.base-url, default
   http://rag-query.platform.svc:8082).

4. ArchUnit test: domain no importa Spring; service usa sólo port; controller
   no en este módulo (Knowledge expone API interna sólo).

5. Tests con MockRestServiceServer para simular respuestas de rag-query y
   confirmar parsing de citation [source: path#section@sha].

OWNERSHIP: platform/knowledge/**.
NO toques: rag-query service, rag-ingestor service.
```

## C3 — Inventory module: wire QueryKnowledgePort para enriquecer Pod detail

**Prompt al architect + backend-dev:**

```
Cuando un usuario abre el detalle de un Pod en el frontend, queremos enriquecer
con runbooks relevantes. Diseño:

1. ARCHITECT: define el contrato — extender InventoryPodDetailDto con un campo
   opcional `relatedRunbooks: List<Citation>`. Documenta la decisión en
   docs/adrs/0007-knowledge-aware-inventory.md (incluye ADR header + decisión +
   alternativas consideradas + consecuencias).

2. BACKEND-DEV: en platform/inventory, inyecta QueryKnowledgePort. Cuando
   el GET /api/v1/inventory/pods/{namespace}/{name} se sirve, llama
   knowledge.query("pod $name namespace $namespace error", topK=3) en paralelo
   y agrega los resultados al DTO.

3. Si knowledge.query devuelve emptyList o lanza excepción, el campo
   `relatedRunbooks` queda vacío — la inventory query NUNCA falla por
   problemas en knowledge (degradation graceful).

4. Tests:
   - InventoryPodDetailServiceTest verifica que se llama a QueryKnowledgePort
   - Test con fake que devuelve emptyList → DTO sin runbooks
   - Test con fake que lanza excepción → DTO sin runbooks (no propagar)

OWNERSHIP: platform/inventory + docs/adrs/0007-*.
```

## C4 — frontend: render relatedRunbooks en PodDetail

**Prompt al frontend-dev (Compose MP Web):**

```
En frontend/composeApp/src/commonMain/kotlin/com/apptolast/platform/ui/screens/inventory/PodDetailScreen.kt,
añade una sección "Runbooks relacionados" debajo de los containers.

REQUISITOS:
- Sólo renderizar si relatedRunbooks.isNotEmpty()
- Cada runbook: badge clicable con texto "[source: path#section@sha]"
- Click abre la pantalla RunbookViewerScreen pasando el path como parámetro
- Material 3 dark, acento #00E676 (sello GreenhouseAdmin)
- Sin fetch adicional — usar el DTO ya enriquecido por backend

NO toques nada fuera de frontend/composeApp/.
```

## Verificación end-of-wave-C

```bash
# Helm chart RAG-ingestor lints ok
helm lint k8s/helm/rag-ingestor/

# Módulo knowledge compila + tests verdes
cd platform && ./gradlew :knowledge:test

# Inventory tests siguen verdes con el nuevo wire
cd platform && ./gradlew :inventory:test

# RAG query alcanzable desde el pod (cuando esté desplegado en cluster)
kubectl -n platform exec deploy/platform-app -- \
  curl -fsS rag-query.platform:8082/actuator/health
```

## Citación

- ADR-0005 (in-memory bus Phase 1): docs/adrs/0005-nats-jetstream-vs-inmemory-bus.md
- ADR-0006 (OTEL shape): docs/adrs/0006-otel-collector-shape.md
- Anti-hallucination policy: feedback_rag_anti_hallucination memoria
