---
title: "Marathon plan: Waves YAML para Fases 3-7"
type: architecture
owner: pablo
last-verified: 2026-05-13
status: draft
phase: meta
tags: [roadmap, agent-teams, automation]
---

# Marathon plan — Waves YAML estructuradas

## Context

Este documento materializa el roadmap de `ARCHITECTURE.md` §4 en un plan ejecutable
por agentes. El roadmap define 8 fases (Fase 0..7) que totalizan ~22 semanas. La
Fase 0 (bootstrap) ya está mergeada en `commit ce91c6a` y la Wave A (Fase 1 —
inventory) está en preparación manual por Pablo. Este archivo recoge únicamente
las waves que serán orquestadas por una routine: **C, D, E, F, G**.

La fuente única de verdad sobre módulos y dependencias es `ARCHITECTURE.md` §3
(tabla de bounded contexts). Cualquier discrepancia entre este marathon-plan y
ARCHITECTURE.md se resuelve a favor del segundo. Si una wave requiere modificar
contratos, debe abrir antes una ADR en `docs/adrs/` siguiendo la convención
numérica existente (0001..0004 ya merge­ados; 0005 reservado a Fase 2; 0006+ para
Fase 3 en adelante).

La routine que parsea este archivo es `routines/phase-orchestrator.yaml`
(cron diario a las 06:00 UTC). Su lógica de ejecución es:

1. `yq eval '.'` sobre este markdown — extrae sólo bloques YAML marcados con
   ```` ```yaml ```` que tengan campo `id:` a nivel raíz de lista.
2. Para cada wave en estado `pending`, comprueba sus `precondition`.
3. Si todas las preconditions están satisfechas (i.e. las waves citadas tienen
   `status: done` y existe el PR mergeado anotado en la tabla de status), la
   routine cambia ese wave a `status: in_progress` y dispara `TaskCreate` en
   batch para todas las tasks cuyo `blockedBy: []` esté vacío.
4. A medida que las tasks completan (PR merged + done_when verificado en CI), se
   liberan las siguientes; las tasks restantes dentro de la misma wave se
   disparan respetando el DAG de `blockedBy`.
5. Cuando todas las tasks de la wave están `done`, la routine cambia
   `status: in_progress → done`, anota el PR en la "Waves status table" y
   comprueba si la siguiente wave puede arrancar.

Restricción operacional: **Wave A (Fase 1 — Inventory) y Wave B (Fase 2 —
Observability) se ejecutan manualmente con Pablo en su CLI Claude Code**. Son las
waves fundacionales: sus contratos y conventions son leídos por todas las waves
posteriores, y el riesgo de un error en knowledge-port o telemetry-port es
demasiado alto para automatizarlo todavía. Una vez Wave B merge­ada, `wave-B-merged`
queda satisfecho y `phase-orchestrator` toma el relevo para C..G.

Mecanismo de continuación post-sesión: la routine es idempotente. Si una sesión
del lead se interrumpe a mitad de Wave D, al día siguiente el cron lee el estado
desde este archivo (status + PRs anotados) y reanuda exactamente donde quedó. Por
eso es **obligatorio** que el lead actualice `status` y la tabla de PRs en el
mismo commit que cierra cada task, no en un commit posterior.

Cita: `ARCHITECTURE.md` §4 (roadmap), `routines/README.md` (mecánica de routines),
`.claude/agents/team-lead.md` §"Diseño del team" (cómo el lead spawnea teammates).

## Waves status table

| Wave | Phase | Status | Started | Merged PR |
|---|---|---|---|---|
| A | 1 — Inventory | pending | — | — |
| B | 2 — Observability | pending | — | — |
| C | 3 — Knowledge/RAG | pending | — | — |
| D | 4 — Identity/Secrets | pending | — | — |
| E | 5 — Automation | pending | — | — |
| F | 6 — Hardening | pending | — | — |
| G | 7 — Topology+Cognee | pending | — | — |

## Wave YAML schema

```yaml
- id: <letter>
  phase: <N>-<short-name>
  precondition: ["wave-X-merged", ...]    # bloqueos
  status: pending|in_progress|done
  duration_estimate_weeks: <N>
  team_size: <N>                          # teammates
  tasks:
    - id: <wave><N>                       # ej. C1, F3
      agent: <role>                       # architect|backend-dev|frontend-dev|qa-engineer|security-reviewer|devops-engineer|tech-writer
      model: opus|sonnet
      blockedBy: [<task-id>, ...]
      ownership_paths: [<glob>, ...]
      prompt: |
        <prompt autosuficiente para el teammate spawn>
      done_when: |
        <criterio ejecutable: comando + expected output>
```

## Wave C — Fase 3 Knowledge/RAG (5 weeks, 5 teammates)

```yaml
- id: C
  phase: 3-knowledge-rag
  precondition: ["wave-B-merged", "ADR-0005-accepted"]
  status: pending
  duration_estimate_weeks: 5
  team_size: 5
  tasks:
    - id: C1
      agent: architect
      model: opus
      blockedBy: []
      ownership_paths: [docs/adrs/0007-*.md, docs/adrs/0008-*.md, platform/knowledge/api/**]
      prompt: |
        Diseña el contrato del módulo knowledge.

        Inputs:
        - Lee ARCHITECTURE.md §3 fila "knowledge"
        - Lee CLAUDE.md §"Segundo cerebro" (5 reglas inquebrantables)
        - Lee docs/adrs/0003-r2r-rag-stack.md y 0004-second-brain-storage-and-knowledge-graph.md

        Output:
        1. ADR-0007: LLM provider para embeddings (OpenAI text-embedding-3-small vs Ollama nomic-embed-text vs Claude API). Justifica coste, latencia, soberanía datos.
        2. ADR-0008: Score threshold para "no encuentro evidencia" (default 0.6 según ARCHITECTURE.md §2.4 — confirma o ajusta con datos).
        3. platform/knowledge/api/:
           - KnowledgePort.kt con `ask(query: Query): Result<AnsweredQuery, NoEvidence>`
           - events/DocumentPublished.kt, DocumentDeprecated.kt, QueryExecuted.kt
           - dto/AnsweredQuery.kt con `answer`, `citations: List<Citation>`, `confidence: Double`

        Cita siempre la fuente con `[source: path]`. No inventes números.
      done_when: |
        - Existen docs/adrs/0007-llm-embeddings-provider.md y 0008-rag-score-threshold.md con status Accepted
        - `./gradlew :platform:knowledge:api:compileKotlin` exit 0
        - SendMessage al lead con resumen de decisiones

    - id: C2
      agent: backend-dev
      model: sonnet
      blockedBy: [C1]
      ownership_paths: [services/rag-ingestor/**]
      prompt: |
        Implementa services/rag-ingestor.

        Inputs:
        - platform/knowledge/api/ (contratos de C1)
        - docs/adrs/0003-r2r-rag-stack.md (R2R como stack RAG)
        - docs/adrs/0007 (provider de embeddings) y 0008 (threshold)

        Build:
        - Watcher inotify sobre carpetas configurables (lista en application.yaml). Default: docs/, runbooks/, ADRs.
        - Pipeline: detect change -> extract text -> chunk (token-based, 512 con overlap 64) -> embed -> upsert en R2R.
        - Publica `DocumentPublished` o `DocumentDeprecated` en NATS subject `knowledge.events`.
        - Idempotencia: hash de contenido -> skip si igual al último ingest.
        - Métricas Micrometer: docs_ingested_total, chunks_total, embedding_latency_seconds.

        Tests:
        - Unit: chunker corner cases (doc vacío, doc >100KB, doc con código fenced)
        - Integration: testcontainer R2R + verificar upsert con un fixture
      done_when: |
        - `./gradlew :services:rag-ingestor:test` exit 0 con cobertura >75%
        - kubectl apply de un docker image local -> watcher detecta un fixture y publica evento NATS verificable con `nats sub knowledge.events`

    - id: C3
      agent: backend-dev
      model: sonnet
      blockedBy: [C1]
      ownership_paths: [services/rag-service/**]
      prompt: |
        Implementa services/rag-service.

        Expone REST `POST /ask` con body `{query: string, top_k?: int}`.

        Flujo:
        1. Embedding de la query (mismo provider de ADR-0007)
        2. Retrieval top_k chunks de R2R
        3. Si max(score) < threshold (ADR-0008): devolver 200 con `{evidence: false, suggestion: ...}` — NO inventar
        4. Si hay evidencia: prompt al LLM con system message "Responde solo con la evidencia dada, cita fuentes con [source: path]"
        5. Validar que la respuesta contiene al menos 1 cita; si no, marcar evidence: false
        6. Devolver `AnsweredQuery` con citations + confidence

        Publica `QueryExecuted` (query hash, latency, evidence_bool, citations) en NATS.

        Tests:
        - Mock R2R y LLM
        - Caso happy path con evidencia
        - Caso sin evidencia (todos los scores < threshold)
        - Caso de alucinación (LLM responde sin citas) -> debe convertir a evidence:false
      done_when: |
        - `./gradlew :services:rag-service:test` exit 0
        - Endpoint responde a curl con la query "¿qué versión de Spring Boot usamos?" devolviendo cita a libs.versions.toml

    - id: C4
      agent: frontend-dev
      model: sonnet
      blockedBy: [C3]
      ownership_paths: [apps/web-admin/src/wasmJsMain/kotlin/knowledge/**]
      prompt: |
        Construye la pantalla "Ask" en Compose Multiplatform Web (wasmJs).

        Componentes:
        - SearchBar (input con debounce 300ms)
        - AnswerCard (markdown render del answer)
        - CitationList (lista clickable, cada cita abre el documento fuente)
        - EvidenceBanner: si `evidence: false`, banner amarillo "No encuentro evidencia. Sugerencia: ..."

        Llama a /ask del rag-service via ktor-client.
        Tema: usa los tokens de docs/design/tokens.json (sincronizado con design system de claude.ai).

        Tests Compose: snapshot por estado (loading, success, no-evidence, error).
      done_when: |
        - `./gradlew :apps:web-admin:wasmJsBrowserTest` exit 0
        - Navegando a /knowledge/ask y enviando una query devuelve respuesta renderizada con citas clickables

    - id: C5
      agent: qa-engineer
      model: sonnet
      blockedBy: [C2, C3, C4]
      ownership_paths: [tests/e2e/knowledge/**]
      prompt: |
        E2E del módulo knowledge.

        Escenarios:
        1. Ingest manual de un doc nuevo -> aparece en /ask en <30s
        2. Borrar un doc -> deja de aparecer en respuestas (DocumentDeprecated propagado)
        3. Query sin evidencia -> response.evidence == false
        4. Query con respuesta inventada por LLM mock -> sistema detecta falta de citas y degrada a evidence:false (anti-alucinación)
        5. Latencia p95 < 3s sobre 100 queries del corpus de docs/

        Stack: testcontainers (R2R + NATS + servicios), JUnit5.
      done_when: |
        - `./gradlew :tests:e2e:knowledge:test` exit 0
        - Reporte de latencias publicado en docs/_live/knowledge-latency.md
```

## Wave D — Fase 4 Identity + Secrets (4 weeks, 4 teammates)

```yaml
- id: D
  phase: 4-identity-secrets
  precondition: ["wave-C-merged"]
  status: pending
  duration_estimate_weeks: 4
  team_size: 4
  tasks:
    - id: D1
      agent: architect
      model: opus
      blockedBy: []
      ownership_paths: [docs/adrs/0009-*.md, docs/adrs/0010-*.md, platform/identity/api/**, platform/secrets/api/**]
      prompt: |
        Diseña los contratos de identity y secrets.

        Inputs:
        - ARCHITECTURE.md §3 filas "identity" y "secrets"
        - Versión confirmada: keycloak-admin-client 26.0.4 (platform/gradle/libs.versions.toml)

        Output:
        1. ADR-0009: Keycloak realm design (un solo realm `apptolast` vs realm por entorno). Decide modelo de roles (admin, operator, viewer) y mapeo a groups.
        2. ADR-0010: Secrets backend. Comparar Passbolt (ya usado por Pablo según dossier) vs Vault vs Sealed Secrets. Justifica.
        3. platform/identity/api/:
           - IdentityPort.kt con `authenticate(token): Result<Principal, AuthError>`, `authorize(principal, action, resource): Boolean`
           - dto/Principal.kt (subject, roles, email)
        4. platform/secrets/api/:
           - SecretsPort.kt con `get(key): Result<SecretValue, NotFound>`, `put(key, value, ttl?)`, `rotate(key)`
           - dto/SecretRef.kt
      done_when: |
        - docs/adrs/0009-keycloak-realm-design.md + 0010-secrets-backend.md status Accepted
        - `./gradlew :platform:identity:api:compileKotlin :platform:secrets:api:compileKotlin` exit 0

    - id: D2
      agent: devops-engineer
      model: sonnet
      blockedBy: [D1]
      ownership_paths: [k8s/identity/keycloak/**]
      prompt: |
        Helm chart de Keycloak en k8s/identity/keycloak/.

        Requisitos:
        - Usa la chart oficial de Bitnami o keycloakx (decide y documenta)
        - Postgres dedicado (no compartir con otros) - Longhorn PVC 5Gi
        - Realm `apptolast` provisionado declarativamente vía configMap con realm.json
        - Ingress con cert-manager (issuer letsencrypt-prod ya existe en el cluster)
        - HPA min 1 max 2 (cluster es single-node, no exagerar)
        - Métricas Prometheus habilitadas (lo consume Wave B - VictoriaMetrics)

        Tests:
        - `helm template` valida sin errores
        - `kubeval` sobre el output
      done_when: |
        - `helm template k8s/identity/keycloak | kubeval --strict` exit 0
        - Documentado en docs/infrastructure/keycloak.md cómo rotar el admin password

    - id: D3
      agent: backend-dev
      model: sonnet
      blockedBy: [D1, D2]
      ownership_paths: [platform/identity/adapter-keycloak/**]
      prompt: |
        Implementa el adapter de IdentityPort contra Keycloak.

        - Usa keycloak-admin-client 26.0.4 (versión exacta del libs.versions.toml)
        - Cache de JWKS en memoria con TTL 10 min
        - Validación de JWT: signature + exp + iss + aud
        - Mapping de roles Keycloak -> Principal.roles
        - Integración con Spring Security 6 (filtro JWT)
        - Tests con testcontainer de Keycloak

        Eventos: publica `PrincipalAuthenticated`, `AuthorizationDenied` en NATS para auditoría.
      done_when: |
        - `./gradlew :platform:identity:adapter-keycloak:test` exit 0
        - Cobertura >80%

    - id: D4
      agent: backend-dev
      model: sonnet
      blockedBy: [D1]
      ownership_paths: [platform/secrets/adapter-passbolt/**]
      prompt: |
        Implementa el adapter de SecretsPort contra Passbolt (decisión de ADR-0010, asumiendo Passbolt).

        - Cliente OpenPGP para auth (Passbolt usa GPG)
        - get(key): resolver vía /resources?filter[search]=key, devolver decrypted secret
        - put(key, value, ttl): crear resource con shares al grupo apptolast-platform
        - rotate(key): generar nuevo valor, llamar put, marcar antiguo como rotated en metadata
        - Audit: cada operación publica `SecretAccessed` (con hash del key, NO el valor) en NATS

        Si ADR-0010 eligió Vault o Sealed Secrets, sustituye el adapter por el correspondiente.
      done_when: |
        - `./gradlew :platform:secrets:adapter-passbolt:test` exit 0
        - Integration test contra Passbolt mock (wiremock) verde

    - id: D5
      agent: devops-engineer
      model: sonnet
      blockedBy: [D4]
      ownership_paths: [k8s/n8n/**, docs/runbooks/migrate-n8n-secret.md]
      prompt: |
        Migración del password de n8n: plaintext env -> Kubernetes Secret (dolor P0 #10 del dossier 2026-05-12).

        Pasos:
        1. Leer el valor actual del Deployment n8n-prod (env DB_POSTGRESDB_PASSWORD o equivalente)
        2. `kubectl create secret generic n8n-db-credentials --from-literal=password=...`
        3. Actualizar Helm values de n8n-prod para referenciar el Secret vía `valueFrom.secretKeyRef`
        4. `helm upgrade n8n-prod ...`
        5. Verificar pod READY 1/1
        6. Rotar password en Postgres y en Secret (probar el flujo completo)
        7. Documentar en docs/runbooks/migrate-n8n-secret.md

        ATENCIÓN: n8n-prod está actualmente en estado Helm `failed` (dossier P0 #3). Coordinar con F3 (wave F) o resolver antes el rollback.
      done_when: |
        - `kubectl -n n8n get deploy n8n-prod -o yaml | grep -c 'plaintext-password'` == 0
        - `kubectl -n n8n get secret n8n-db-credentials` existe
        - Runbook mergeado

    - id: D6
      agent: qa-engineer
      model: sonnet
      blockedBy: [D3, D4]
      ownership_paths: [tests/security/rbac/**]
      prompt: |
        Suite de tests RBAC end-to-end.

        Escenarios (matriz role x action x resource):
        - admin: todo permitido
        - operator: read + write a runbooks; read a secrets; nada a ADRs
        - viewer: solo read
        - anonymous: 401 en todo endpoint protegido

        Stack: REST Assured + testcontainer Keycloak.
      done_when: |
        - `./gradlew :tests:security:rbac:test` exit 0 con 100% de casos pasando
        - Matriz documentada en docs/_live/rbac-matrix.md

    - id: D7
      agent: security-reviewer
      model: opus
      blockedBy: [D3, D4, D5]
      ownership_paths: [docs/_live/security-audit-wave-D.md]
      prompt: |
        Security audit de la wave D.

        Checklist:
        - ¿Tokens JWT validan signature + exp + iss + aud?
        - ¿JWKS cache tiene TTL razonable (<= 1h)?
        - ¿Secrets nunca aparecen en logs? (grep por keywords sensibles en logs de los servicios)
        - ¿NATS subject de auditoría es read-only para los servicios? (no pueden borrar audit events)
        - ¿Keycloak admin password está rotado desde el default?
        - ¿RBAC matrix de D6 cubre todos los recursos sensibles?

        Output: docs/_live/security-audit-wave-D.md con findings clasificados (critical/high/medium/low).
      done_when: |
        - Audit doc creado, cero findings critical pendientes
        - Findings high con ticket asignado en Wave F

    - id: D8
      agent: tech-writer
      model: sonnet
      blockedBy: [D7]
      ownership_paths: [docs/runbooks/identity-onboarding.md, docs/runbooks/secret-rotation.md]
      prompt: |
        Runbooks operacionales.

        1. docs/runbooks/identity-onboarding.md:
           - Cómo dar de alta un usuario nuevo
           - Cómo asignar roles
           - Cómo revocar acceso
        2. docs/runbooks/secret-rotation.md:
           - Cómo rotar un secret (proceso manual de fallback)
           - Cómo rotar todos los secrets de un servicio
           - SLA: secrets de DB rotados cada 90 días

        Frontmatter completo siguiendo docs/_template.md.
      done_when: |
        - 2 runbooks mergeados con frontmatter válido (yq parsea bien)
```

## Wave E — Fase 5 Automation (3 weeks, 3 teammates)

```yaml
- id: E
  phase: 5-automation
  precondition: ["wave-D-merged"]
  status: pending
  duration_estimate_weeks: 3
  team_size: 3
  tasks:
    - id: E1
      agent: architect
      model: opus
      blockedBy: []
      ownership_paths: [docs/adrs/0011-*.md, platform/automation/api/**]
      prompt: |
        Contratos del módulo automation.

        Modelo de dominio:
        - JobExecution: id, jobRef, startedAt, finishedAt, status (running|success|failed), output (truncated)
        - ScheduledTask: id, name, cron, target (K8sCronJob | N8nWorkflow | HostCommand), enabled
        - Trigger: manual | scheduled

        Output:
        1. ADR-0011: cómo unificar 3 mundos de scheduling (K8s CronJobs ~18 activos según dossier, n8n workflows, crontabs del host). Decide adaptadores.
        2. platform/automation/api/:
           - AutomationPort.kt con `listSchedules()`, `triggerNow(ScheduledTaskId, principal)`, `getExecution(id)`
           - events/JobStarted.kt, JobFinished.kt, ScheduleChanged.kt
      done_when: |
        - ADR-0011 status Accepted
        - `./gradlew :platform:automation:api:compileKotlin` exit 0

    - id: E2
      agent: backend-dev
      model: sonnet
      blockedBy: [E1]
      ownership_paths: [platform/automation/adapter-k8s/**, platform/automation/adapter-n8n/**, platform/automation/adapter-host/**]
      prompt: |
        Tres adaptadores:

        1. adapter-k8s: usa fabric8-kubernetes-client 7.0.1 (versión exacta de libs.versions.toml). Lista CronJobs, lanza `kubectl create job --from=cronjob/X` programáticamente, watch del Job.
        2. adapter-n8n: cliente REST contra n8n API (https://<n8n-host>/api/v1/workflows). Lista workflows, ejecuta `/workflows/{id}/execute`.
        3. adapter-host: DaemonSet que ejecuta los crontabs del host (NO se modifica el host directo; el DaemonSet monta /var/spool/cron como ro y publica las entries; ejecuciones via SSH a Hetzner o nsenter — decide en E1).

        Cada adapter publica JobStarted/JobFinished en NATS.

        Tests con testcontainer (K8s: kind; n8n: container oficial).
      done_when: |
        - `./gradlew :platform:automation:adapter-k8s:test :platform:automation:adapter-n8n:test :platform:automation:adapter-host:test` exit 0

    - id: E3
      agent: backend-dev
      model: sonnet
      blockedBy: [E1]
      ownership_paths: [services/automation-api/**]
      prompt: |
        REST API para trigger manual con auditoría.

        Endpoints:
        - GET /schedules -> List<ScheduledTask>
        - GET /schedules/{id}/executions?limit=50 -> last 50 JobExecution
        - POST /schedules/{id}/trigger (requiere role operator+) -> dispara y devuelve execution id
        - GET /executions/{id} -> detalle (incluye output truncado a 10KB)

        Auditoría: cada POST publica `JobTriggered` (incluyendo principal, motivo opcional en body) en subject `automation.audit`.

        Rate limit: 10 triggers/min por principal.
      done_when: |
        - `./gradlew :services:automation-api:test` exit 0
        - curl con token operator a /schedules devuelve lista (en entorno test con kind)

    - id: E4
      agent: frontend-dev
      model: sonnet
      blockedBy: [E3]
      ownership_paths: [apps/web-admin/src/wasmJsMain/kotlin/automation/**]
      prompt: |
        UI cronjob-board (extiende la existente del proyecto cluster-ops si la hubiera; si no, créala).

        Componentes:
        - SchedulesTable: lista con cron expression human-readable (próxima ejecución), source (k8s/n8n/host), enabled badge
        - TriggerButton: con modal de confirmación (input opcional de "motivo")
        - ExecutionDrawer: detalle de la ultima ejecución (status, logs)
        - RealtimeBadge: WebSocket subscribe a NATS `automation.audit` para refrescar lista

        Tema: tokens de docs/design/tokens.json.
      done_when: |
        - `./gradlew :apps:web-admin:wasmJsBrowserTest` exit 0
        - Manual: pulsar Trigger en un CronJob de prueba -> aparece nueva ejecución en <5s
```

## Wave F — Fase 6 Hardening (3 weeks, 5 teammates) — ATACA 17 DOLORES DEL DOSSIER

Esta wave aborda los 17 dolores documentados en el dossier de auditoría
2026-05-12 (cluster-ops/audit/). El orden de tasks respeta la prioridad
P0 > P1 > P2.

```yaml
- id: F
  phase: 6-hardening
  precondition: ["wave-E-merged"]
  status: pending
  duration_estimate_weeks: 3
  team_size: 5
  tasks:
    - id: F1
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/longhorn/backup-target.yaml, k8s/longhorn/cronjob-snapshot-upload.yaml, docs/runbooks/longhorn-disaster-recovery.md]
      prompt: |
        DOLOR P0 #1 — Longhorn backup off-site.

        Contexto: Longhorn corre single-replica SIN backup remoto (dossier 2026-05-12).
        Solución elegida: Hetzner Storage Box RB100 (~3.50€/mes, 100Gi). Endpoint S3-compatible.

        Pasos:
        1. Provisionar Storage Box (manual en panel Hetzner — pedir credenciales a Pablo).
        2. Crear Secret con S3 credentials en namespace longhorn-system.
        3. `kubectl -n longhorn-system patch setting backup-target --type merge -p '{"value":"s3://<bucket>@<region>/"}'`
        4. CronJob nightly 03:00 UTC que ejecute snapshot de todos los PVs marcados con label `backup=nightly`, y trigger backup-job.
        5. Runbook de disaster-recovery en docs/runbooks/longhorn-disaster-recovery.md.

        Test de DR: borrar un PV de prueba y restaurarlo desde Storage Box.
      done_when: |
        - `kubectl -n longhorn-system get setting backup-target -o jsonpath='{.value}' | grep -qE '^s3://'`
        - CronJob `longhorn-nightly-backup` existe y completó al menos 1 ejecución
        - Runbook mergeado

    - id: F2
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/cronjobs/pgdump/**, docs/runbooks/restore-postgres.md]
      prompt: |
        DOLOR P0 #2 — pg_dump cronjobs para 9 bases de datos.

        Bases (del dossier):
        - 7 Postgres: n8n, invernadero-metadata, inemsellar, menus, whoop, langflow, shlink
        - 1 TimescaleDB
        - 1 MySQL gibbon

        Para cada DB:
        - CronJob diario 02:00 UTC
        - Dump comprimido (gzip)
        - Upload al mismo Storage Box de F1, prefijo `db-dumps/<dbname>/<YYYY-MM-DD>.sql.gz`
        - Retention: 30 días en Storage Box, 7 días en PVC local
        - Métricas Prometheus: dump_duration_seconds, dump_size_bytes, dump_last_success_timestamp

        Runbook: docs/runbooks/restore-postgres.md con receta de restore-from-dump.
      done_when: |
        - `kubectl get cronjob -A | grep -c pgdump-` == 9 (los 9 DB, ajustar contador si MySQL gibbon tiene cronjob distinto)
        - Tras 24h, los 9 cronjobs tienen al menos 1 ejecución successful
        - Runbook mergeado

    - id: F3
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/n8n/**, docs/runbooks/helm-rollback-n8n.md]
      prompt: |
        DOLOR P0 #3 — n8n-prod Helm release está en estado `failed` desde 2025-12-22 (dossier).

        Investigar y resolver:
        1. `helm history n8n-prod -n n8n` para ver qué revision falló
        2. Comprobar pods, eventos, logs
        3. Si la última revisión healthy está disponible -> `helm rollback n8n-prod <rev>`
        4. Si no -> redeploy desde values.yaml limpios

        IMPORTANTE: coordinar con D5 (migración de password a Secret). Idealmente F3 antes que D5, pero D5 puede esperar a F3.

        Runbook: docs/runbooks/helm-rollback-n8n.md con la receta general.
      done_when: |
        - `helm status n8n-prod -n n8n` reporta `deployed`
        - `kubectl -n n8n get pods` todos Ready
        - Runbook mergeado

    - id: F4
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/storage/containerd-volume.yaml, docs/runbooks/migrate-containerd-volume.md]
      prompt: |
        DOLOR P0 #4 — Mover /var/lib/containerd a un HC Volume.

        Disco raíz actual: 73% (dossier 2026-05-12). /var/lib/containerd ocupa ~48G.

        Pasos:
        1. Crear Hetzner Cloud Volume 100Gi en la misma zona que el VPS
        2. Attach al servidor
        3. Format ext4 + mount en /mnt/containerd-vol
        4. `systemctl stop containerd kubelet`
        5. rsync de /var/lib/containerd/ a /mnt/containerd-vol/
        6. Update /etc/fstab para montar /mnt/containerd-vol en /var/lib/containerd
        7. `systemctl start kubelet containerd`
        8. Verificar pods vuelven Ready

        VENTANA DE MANTENIMIENTO: requiere downtime (~15 min). Coordinar con Pablo.

        Runbook con el procedimiento exacto.
      done_when: |
        - `df -h /var/lib/containerd | tail -1` muestra mount point del HC Volume
        - `df -h /` muestra disco raíz <60%
        - Todos los nodes Ready, todos los pods running

    - id: F5
      agent: security-reviewer
      model: opus
      blockedBy: []
      ownership_paths: [k8s/networkpolicy/db-isolation.yaml, docs/runbooks/db-network-isolation.md]
      prompt: |
        DOLOR P0 #5 — Cerrar NodePorts de DBs expuestos al internet.

        NodePorts del dossier:
        - TimescaleDB 30432
        - PG metadata 30433
        - PG whoop 30434
        - Redis 30379
        - EMQX 30883, 30884, 30180, 30081, 30083, 30084

        Opciones:
        (a) NetworkPolicy denegando ingress excepto desde pods con label `db-client=true` + WireGuard pod
        (b) Hetzner Cloud Firewall bloqueando esos puertos desde 0.0.0.0/0 excepto IPs allowlist
        (c) Cambiar todos los Service a ClusterIP y forzar acceso via WireGuard

        Recomendación: (b) + (c). Eliminar NodePort donde sea posible.

        Para servicios que necesitan acceso externo legítimo (raro), exponer via Ingress con TLS y auth.
      done_when: |
        - `nmap -p 30432,30433,30434,30379,30883,30884,30180,30081,30083,30084 <vps-ip>` desde fuera de WireGuard reporta filtered/closed para todos
        - Runbook documenta cómo añadir/quitar IP del allowlist

    - id: F6
      agent: devops-engineer
      model: sonnet
      blockedBy: [D5]
      ownership_paths: [k8s/n8n/secret-migration-verification.md]
      prompt: |
        DOLOR P0 #6 — Password n8n env plaintext -> Secret.

        Esta task verifica que D5 está aplicado en producción y cierra el dolor.
        Si D5 sigue pending por bloqueo de F3, F6 espera.

        Verificación:
        - `kubectl -n n8n get deploy n8n-prod -o yaml | grep -i password` no devuelve valores en claro
        - El Secret `n8n-db-credentials` existe y el deployment lo referencia
        - n8n se conecta correctamente a Postgres tras rotación de prueba
      done_when: |
        - Comando de verificación pasa
        - Notar en CHANGELOG: P0 #6 cerrado

    - id: F7
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/wireguard/**, docs/runbooks/wireguard-recovery.md]
      prompt: |
        DOLOR P0 #7 — WireGuard pod con imagen :latest.

        Pasos:
        1. Identificar la imagen actual: `kubectl -n wireguard get deploy -o yaml | grep image:`
        2. Resolver el digest actual: `docker inspect --format='{{index .RepoDigests 0}}' <image>`
        3. Pin a versión específica (NO :latest, ej: linuxserver/wireguard:1.0.20210914-r0 — consultar Docker Hub para versión estable actual)
        4. Backup del PVC con la config (conf de peers): snapshot Longhorn + dump a /docs/_live/wireguard-config-backup.md.gpg (encrypted)
        5. Apply nueva imagen
        6. Verificar todos los peers reconectan

        Runbook: cómo restaurar peers desde el backup.
      done_when: |
        - `kubectl -n wireguard get deploy -o yaml | grep image:` muestra tag con versión (no :latest)
        - Backup encrypted creado y verificable

    - id: F8
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/cert-manager/cleanup-orphan-certs.yaml]
      prompt: |
        DOLOR P1 #8 — Eliminar certs huérfanos en cert-manager.

        Hostnames a eliminar (dossier):
        - prometheus.*, grafana.*, alertmanager.*, kali.*, generator-ui.*, llm-router.*, rag-service.*

        Pasos:
        1. `kubectl get certificate -A | grep -E '(prometheus|grafana|alertmanager|kali|generator-ui|llm-router|rag-service)'`
        2. Para cada uno: validar que NO está en uso (ingress o servicio que lo monte)
        3. `kubectl delete certificate <name> -n <ns>`
        4. Eliminar también los Ingress y CertificateRequest asociados
        5. Reduce el ruido del log de cert-manager (re-emisión cada 90d innecesaria)

        EXCEPCIÓN: rag-service.* puede ser necesario tras Wave C — confirmar con el output de Wave C antes de eliminar.
      done_when: |
        - Cero certificados huérfanos en `kubectl get certificate -A`
        - Logs de cert-manager sin spam de renewals fallidos

    - id: F9
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/cleanup/rke2-residuals.md]
      prompt: |
        DOLOR P1 #9 — Limpiar residuos de RKE2 en cluster fleet-default.

        Contexto: el cronjob `rke2-machineconfig-cleanup` sigue activo aunque el cluster ya no usa RKE2 (kubeadm v1.32.3 según dossier).

        Pasos:
        1. Listar todos los recursos en namespaces `cattle-*`, `fleet-*`, `rke2-*`
        2. Validar que ninguno está en uso (annotaciones, owners)
        3. Eliminar el cronjob `rke2-machineconfig-cleanup`
        4. Eliminar namespaces vacíos al final

        Documenta en docs/runbooks/cleanup-rke2.md la lista de qué se borró.
      done_when: |
        - `kubectl get cronjob -A | grep rke2` no devuelve resultados
        - Runbook mergeado con lista de recursos eliminados

    - id: F10
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/cronjobs/cluster-healthcheck-discord.yaml]
      prompt: |
        DOLOR P1 #10 — Re-habilitar cluster-healthcheck-discord o eliminarlo.

        Estado actual: SUSPENDED (dossier).

        Decisión:
        - Si el webhook de Discord sigue siendo válido y el equipo lo usa -> re-habilitar (`kubectl patch cronjob cluster-healthcheck-discord --type=merge -p '{"spec":{"suspend":false}}'`)
        - Si no -> eliminar (`kubectl delete cronjob cluster-healthcheck-discord`) y migrar la lógica a la pila de observabilidad de Wave B (Alertmanager / VictoriaLogs)

        Decidir con Pablo en SendMessage antes de actuar.
      done_when: |
        - Cronjob ya NO está en estado `suspended` (o no existe)
        - Decisión documentada en CHANGELOG

    - id: F11
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [docs/runbooks/enable-hcloud-backup.md]
      prompt: |
        DOLOR P1 #11 — Activar Hetzner Cloud Backup en el panel.

        Pasos:
        1. Login en Hetzner Cloud Console (manual)
        2. Server -> Backups -> Enable (coste +20% del precio del servidor)
        3. Confirmar políticas: daily snapshots, retention 7 days
        4. Documentar en docs/runbooks/enable-hcloud-backup.md el procedimiento + cómo restaurar

        Nota: la confirmación de activación es manual; F11 cierra cuando Pablo lo confirme vía SendMessage.
      done_when: |
        - Runbook mergeado
        - SendMessage de Pablo confirmando "backup activado"

    - id: F12
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/tooling/hcloud-cli.md]
      prompt: |
        DOLOR P2 #12 — Instalar hcloud CLI + token.

        Pasos:
        1. Generar token en Hetzner Cloud (panel) con permisos read-write (o read si suficiente)
        2. Guardar token como Secret en namespace `platform-tooling`
        3. Instalar hcloud CLI en el bastion (host del VPS o pod admin) — versión actual estable
        4. Smoke test: `hcloud server list` devuelve el VPS CPX62

        Documentar en docs/tooling/hcloud-cli.md cómo usarlo y rotar el token.
      done_when: |
        - `hcloud server list` ejecutado correctamente desde el entorno admin
        - Token almacenado solo en Secret, jamás en plaintext

    - id: F13
      agent: tech-writer
      model: sonnet
      blockedBy: []
      ownership_paths: [docs/operations/cronjobs-baseline.md]
      prompt: |
        DOLOR P2 #13 — Documentar baseline de los 18 cronjobs cluster-ops.

        Inputs:
        - `kubectl get cronjob -A -o yaml`
        - Archivos en cluster-ops/audit/RUNBOOKS/ (27 runbooks existentes)

        Para cada cronjob, documentar:
        - Nombre, namespace
        - Schedule (cron + human-readable)
        - Propósito (1 línea)
        - Inputs / Outputs
        - Quién lo creó / cuándo
        - Estado actual (active, suspended)
        - Link a runbook si existe

        Formato: tabla markdown + sección por cronjob.
      done_when: |
        - docs/operations/cronjobs-baseline.md cubre los 18 cronjobs
        - Frontmatter válido

    - id: F14
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/cleanup/empty-namespaces.md]
      prompt: |
        DOLOR P2 #14 — Eliminar namespaces vacíos.

        Lista del dossier:
        - apptolast
        - apptolast-greenhouse-admin-prod

        Verificar:
        - `kubectl -n <ns> get all,secret,cm,pvc` devuelve solo recursos default
        - Ningún ownerReference apunta a recursos de esos NS

        Si vacíos: `kubectl delete namespace <ns>`.

        Si tienen residuos, documentar qué queda en docs/runbooks/empty-namespaces-investigation.md.
      done_when: |
        - `kubectl get ns | grep -E '^(apptolast|apptolast-greenhouse-admin-prod)\\s'` no devuelve resultados, O bien runbook con investigación mergeado

    - id: F15
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [scripts/rotate-n8n-backup.sh, docs/runbooks/n8n-backup-rotation.md]
      prompt: |
        DOLOR P2 #15 — Política de rotación de /home/admin/n8n-backup/.

        Estado: ocupa 29G (dossier 2026-05-12).

        Política:
        - Retención local: 7 días
        - Retención off-site (Storage Box de F1): 30 días
        - Compresión zstd
        - Cronjob host (o DaemonSet, decide alineado con E2) que ejecuta rotation diaria

        Script bash idempotente en scripts/rotate-n8n-backup.sh + runbook.
      done_when: |
        - Script + cronjob/timer aplicado
        - Tras 1 semana, /home/admin/n8n-backup/ ocupa <10G

    - id: F16
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/storage/orphan-pvs-cleanup.md]
      prompt: |
        DOLOR P2 #16 — PVs huérfanos en estado Available.

        Lista del dossier (storageClass local-storage):
        - emqx-pv
        - postgresql-metadata-pv
        - redis-pv
        - timescaledb-pv

        Para cada PV:
        1. Confirmar que no tiene claim asociado (`kubectl get pv <name> -o jsonpath='{.spec.claimRef}'` vacío)
        2. Confirmar que su directorio host NO contiene datos necesarios (revisar /var/lib/local-storage o equivalente)
        3. Backup del contenido al Storage Box antes de eliminar
        4. `kubectl delete pv <name>`

        ATENCIÓN: local-storage; el delete del PV no borra los datos del host. Limpieza manual del path posterior.
      done_when: |
        - `kubectl get pv | grep -cE '(emqx|postgresql-metadata|redis|timescaledb)-pv'` == 0
        - Backups verificados en Storage Box

    - id: F17
      agent: architect
      model: opus
      blockedBy: []
      ownership_paths: [docs/adrs/0012-no-kube-state-metrics.md]
      prompt: |
        DOLOR P2 #17 — Decisión sobre kube-state-metrics + Prometheus operator.

        Según ARCHITECTURE.md §7 (anti-scope-creep): NO instalar.

        Tarea: escribir ADR-0012 que documente la decisión con justificación:
        - VictoriaMetrics (Wave B) es ~10× más ligero que Prometheus operator
        - kube-state-metrics se sustituye por exporters específicos según necesidad
        - Si en el futuro hace falta una métrica de estado de un objeto K8s concreto, se añade exporter puntual

        ADR status: Accepted. Cierra el dolor 17 como "decisión arquitectural, no acción técnica".
      done_when: |
        - docs/adrs/0012-no-kube-state-metrics.md status Accepted, mergeado
        - CHANGELOG anota P2 #17 cerrado por ADR
```

## Wave G — Fase 7 Topology + Cognee + Design system (4 weeks, 4 teammates)

```yaml
- id: G
  phase: 7-topology-cognee-design
  precondition: ["wave-F-merged"]
  status: pending
  duration_estimate_weeks: 4
  team_size: 4
  tasks:
    - id: G1
      agent: architect
      model: opus
      blockedBy: []
      ownership_paths: [docs/adrs/0013-*.md, platform/knowledge/api/**]
      prompt: |
        Integración de Cognee como segundo RAG backend.

        Contexto: R2R (ADR-0003) es el backend principal del módulo knowledge. Cognee aporta knowledge-graph nativo (entidades + relaciones) que R2R no tiene.

        Output:
        1. ADR-0013: estrategia dual RAG. Cuándo usar R2R (búsqueda semántica simple) vs Cognee (queries de relación). Mecanismo de fan-out o routing.
        2. Extender KnowledgePort si es necesario (mantener backward compat con C1).

        Decisión clave: routing transparente o explícito (cliente elige backend).
      done_when: |
        - ADR-0013 status Accepted
        - Cambios de API son backward-compatible (verificable con tests de Wave C)

    - id: G2
      agent: backend-dev
      model: sonnet
      blockedBy: [G1]
      ownership_paths: [platform/knowledge/adapter-cognee/**, services/knowledge-router/**]
      prompt: |
        Implementa adapter-cognee + knowledge-router.

        - adapter-cognee: cliente Python REST contra Cognee (proxy en Kotlin si Cognee solo expone Python SDK). Mapea KnowledgePort a operaciones Cognee.
        - knowledge-router: servicio que recibe queries, decide backend (R2R o Cognee) según heurística de ADR-0013, fan-out si la heurística no es clara.
        - Resultados fusionados: deduplicate por citation path, score reranked.

        Tests con testcontainers (R2R + Cognee + servicios).
      done_when: |
        - `./gradlew :platform:knowledge:adapter-cognee:test :services:knowledge-router:test` exit 0
        - Query "qué servicios dependen de Postgres" devuelve relación clara (graph capability)

    - id: G3
      agent: frontend-dev
      model: sonnet
      blockedBy: []
      ownership_paths: [apps/web-admin/src/wasmJsMain/kotlin/topology/**]
      prompt: |
        UI TopologyGraph (force-directed graph).

        Stack:
        - Compose Multiplatform Web 1.10.2 (wasmJs) — versión exacta de libs.versions.toml
        - Librería de grafos: evaluar entre canvas custom vs interop con d3-force vía JS (decide y documenta en código)

        Datos:
        - Nodos: servicios, bases de datos, namespaces, dependencias externas
        - Aristas: dependency (HTTP, NATS, DB), ownership
        - Fuente: agregador que combina inventory (Wave A) + topology de NATS subjects + dependency graph de Gradle

        Interacciones:
        - Filtrado por bounded context
        - Click en nodo: panel lateral con detalles
        - Doble click: expand/collapse subgrafo

        Tema: tokens.json del design system.
      done_when: |
        - `./gradlew :apps:web-admin:wasmJsBrowserTest` exit 0
        - Demo con 50 nodos + 100 aristas renderiza fluidamente (>30 FPS)

    - id: G4
      agent: devops-engineer
      model: sonnet
      blockedBy: []
      ownership_paths: [k8s/ai/langflow.yaml, k8s/ai/open-webui.yaml, docs/runbooks/ai-tools.md]
      prompt: |
        Integrar Langflow + Open WebUI en el cluster.

        - Langflow: chart oficial, ingress con auth via Keycloak (Wave D), PVC Longhorn 5Gi
        - Open WebUI: chart, mismo patrón
        - Ambos exponen su API al rag-service (Wave C) para experimentos

        Documentar en docs/runbooks/ai-tools.md cómo usarlos y conectarlos a flujos de n8n.
      done_when: |
        - `kubectl -n ai get pods` muestra langflow y open-webui Ready
        - Acceso via ingress autenticado por Keycloak

    - id: G5
      agent: tech-writer
      model: sonnet
      blockedBy: []
      ownership_paths: [docs/design/tokens.json, docs/design/sync-from-claude-ai.md]
      prompt: |
        Export del design system de claude.ai/design a tokens.json.

        Pasos:
        1. Descargar tokens (colores, tipografía, spacing, radios) de claude.ai/design
        2. Mapear a estructura tokens.json compatible con Compose theme
        3. Sincronizar al theme de apps/web-admin (Compose ColorScheme + Typography)
        4. Documentar el proceso de sync en docs/design/sync-from-claude-ai.md (manual, no automatizado por ahora)
      done_when: |
        - docs/design/tokens.json contiene al menos 30 tokens
        - Theme de Compose lee desde tokens.json sin valores hardcoded
        - Doc de sync mergeado
```

## Mecanismo de progreso

Cuando una wave completa:

1. Lead actualiza status en este archivo: `status: in_progress -> done` (en el
   bloque YAML correspondiente).
2. Anota el PR mergeado en la "Waves status table" arriba.
3. Commit en una sola operación: `chore(marathon): wave X done`.
4. `phase-orchestrator` (cron 06:00 UTC) detecta `wave-X-merged` en la
   `precondition` de la siguiente wave y dispara los TaskCreate.

Para tareas dentro de una wave en progreso:

- El lead spawnea teammates respetando `blockedBy`.
- Al cerrar cada task (PR merged + done_when verificado), el lead actualiza el
  estado de la task dentro del bloque YAML (puede añadir comentario inline
  `# done <date> by <agent>`).

## Reglas

- Cada prompt de task debe ser **autosuficiente**: los teammates NO heredan el
  contexto del lead. Asumir que el teammate solo recibe el campo `prompt` + los
  paths citados.
- **Cita siempre la fuente**: cualquier afirmación factual lleva
  `[source: <path>]`. Si la afirmación no tiene fuente, no se hace.
- **Modelos**: Opus para `architect`, `security-reviewer`, `code-reviewer`,
  `mentor`. Sonnet para el resto (`backend-dev`, `frontend-dev`, `qa-engineer`,
  `devops-engineer`, `tech-writer`).
- **Cleanup obligatorio**: el lead invoca `cleanup_team` al final de cada wave.
- **Cero scope creep**: si un teammate propone trabajo fuera de `ownership_paths`,
  abrir issue separada en lugar de extender la task.
- **ADRs antes de código**: cualquier decisión arquitectural se documenta en ADR
  *antes* de implementarse. PRs de código que dependan de una ADR la citan en el
  cuerpo del PR.
