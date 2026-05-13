---
title: "Service: rag-ingestor"
type: service
owner: pablo
last-verified: 2026-05-13
source-of-truth: services/rag-ingestor/
audience: [backend-dev, devops, oncall]
applies-to-phase: 3 (Knowledge/RAG)
---

# rag-ingestor

CronJob que mantiene fresca la base vectorial (pgvector) con embeddings
de los markdown del repo. Corre cada 15 min. Salida principal: tabla
`vector_store` con chunks + embeddings + metadata `{path, section, sha}`
que sirven la regla **anti-hallucination** [[feedback_rag_anti_hallucination]].

## Cuándo se ejecuta

- Cron `*/15 * * * *` (cada 15 min, defecto en `values.yaml`).
- `concurrencyPolicy: Forbid` — si el job previo aún corre, el siguiente se omite.
- `startingDeadlineSeconds: 300` — si el controlador no puede arrancar el job en 5 min, se descarta.

## Qué hace

1. Clona `git@github.com/apptolast/sistema-central-admin-servidor.git` rama `main` a `/workdir` (ephemeral PVC).
   - Citación: `services/rag-ingestor/src/main/kotlin/com/apptolast/ragingestor/git/GitRepoPoller.kt`.
2. Recorre `docs/**/*.md`, calcula `sha-short`.
3. Chunkea cada markdown por sección H2/H3 (`MarkdownChunkingTest` cubre el caso de acentos via NFD normalize).
4. Embeddea cada chunk con Spring AI `text-embedding-3-small` (1536 dims).
5. Persiste en `vector_store` vía Spring AI `VectorStore.add()` con metadata `{path, section, sha}`.
   - Schema: `services/rag-ingestor/src/main/resources/db/migration/V1__rag_init.sql`.
6. Marca el último `git sha` procesado en tabla `rag_ingest_state` (single-row constraint) para que el siguiente run sea incremental.

## Seguridad

- ServiceAccount sin RBAC. `automountServiceAccountToken: false`.
- `runAsNonRoot: true`, `readOnlyRootFilesystem: true`, drop ALL capabilities.
- Volúmenes:
  - `workdir` ephemeral 5Gi (descartado tras el job).
  - `tmp` emptyDir 256Mi (writable, no persistente).
- Secrets vía `secretKeyRef`:
  - `rag-ingestor-db` (username/password)
  - `rag-ingestor-openai` (api-key)
  - opcional `rag-ingestor-git` (token para repo privado)
- NetworkPolicy default-deny (instalada por el chart `platform-app`); se permite egress sólo a DB + GitHub + OpenAI.

## Despliegue

```bash
helm lint k8s/helm/rag-ingestor/
helm install rag-ingestor k8s/helm/rag-ingestor/ --namespace platform --create-namespace --dry-run

# Producción:
helm upgrade --install rag-ingestor k8s/helm/rag-ingestor/ \
  --namespace platform \
  --create-namespace
```

## Verificación

```bash
kubectl -n platform get cronjobs
kubectl -n platform get jobs --sort-by=.metadata.creationTimestamp | tail
kubectl -n platform logs job/<job-name> --tail 200

# Tabla vector_store debe crecer tras el primer run:
kubectl -n n8n exec postgres-vector-0 -- psql -U $POSTGRES_USER -d rag \
  -c "SELECT count(*) FROM vector_store;"
```

## Triggers manuales

```bash
kubectl -n platform create job --from=cronjob/rag-ingestor rag-ingestor-manual-$(date +%s)
```

## Citación

- Code: `services/rag-ingestor/src/main/kotlin/com/apptolast/ragingestor/embed/MarkdownDocIngester.kt`
- Schema: `services/rag-ingestor/src/main/resources/db/migration/V1__rag_init.sql`
- Anti-hallucination policy: [[feedback_rag_anti_hallucination]]
- Wave C plan: `docs/operations/wave-C-prompts.md#C1`
