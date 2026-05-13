# Changelog

Todas las modificaciones notables de este proyecto se documentan aquí.

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Adherencia a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

### Fixed — Post-merge stabilization 2026-05-13 (CI verde + close pending TODOs)

- **CI schema validation** (PR #2, 3434864) — `secrets.*` no es accesible en step `if:` (limitación de GitHub Actions). Workflow estaba marcando 18 runs consecutivos como Failure con 0 jobs ejecutados desde commit ec2b807 (mucho antes del marathon). Fix: job-level `env: { DH_USER, DH_TOKEN }` con `secrets.X`, y step `if: env.DH_USER != ''`. Confirmado con `actionlint`.
- **docker-frontend job build** (PR #3) — `gradle :composeApp:wasmJsBrowserDistribution` dentro del Dockerfile fallaba con exit 127 en `kotlinWasmToolingSetup` (la imagen eclipse-temurin:21-jdk no tiene Node/curl). Refactor: el job `frontend-build` ya produce el artifact `frontend-dist`; docker-frontend ahora hace `actions/download-artifact@v4` y el Dockerfile es nginx-only (~10x más rápido).
- **CI tags pattern** (PR #3) — el patrón `${{ ... && 'latest' || '' }}` producía línea vacía rompiendo `docker/build-push-action`. Refactor a step previo "Compute tag list" con heredoc → multi-line steps output, deterministic.
- **PodDashboardScreen wired** (PR #3) — antes lista hardcoded `mockPods()` con 5 pods inventados. Ahora `LaunchedEffect(refreshTick) → InventoryClient.listPods()` con try/catch defensivo → emptyList en fallo. Refresh button funciona. Filter button abre fila de FilterChips con namespaces distintos del backend.
- **RagQueryScreen wired** (PR #4) — nuevo `RagClient` data class llama POST /api/v1/rag/query al microservicio rag-query. `RagAnswer` sealed (Cited|NoEvidence). Anti-hallucination: cualquier error → NoEvidence con mensaje canónico. Citation chips renderizadas en FlowRow para wrap.
- **rag-ingestor soft-delete** (PR #4) — TODO de Phase 3 cerrado. Cuando git pull detecta `.md` eliminado, `softDeleteChunks(path)` usa Spring AI `FilterExpressionBuilder.eq("path", relativePath)` para borrar chunks del pgvector. runCatching wrap para que un fallo NO tumbe la siguiente iteración.

**Estado post-fix:**
- CI run #20 main: **SUCCESS** (13/13 jobs green tras PR #3).
- TODOs reales restantes en código: 3 (todos legítimos waits-for-other-work):
  - `LoginScreen.kt`: Phase 4 D4 OIDC redirect (necesita Keycloak vivo)
  - `PassboltApiClient.kt`: Wave-D D4 (necesita Passbolt API contract)
  - `platform/build.gradle.kts`: Phase 1 re-habilitar ktlint cuando publiquen Kotlin 2.3-compatible

### Added — Marathon mega-session 2026-05-13 (cierre Phase 3 + Phase 5, scaffold Phase 4, P0 Phase 6, CI/CD para 5 imágenes)

- **Wave-C C4** (c1aa386) — PodDetailScreen frontend wired al endpoint real
  `/api/v1/inventory/pods/{ns}/{name}`. PodDetailDto + RunbookCitationDto +
  getPodDetail() en InventoryClient con try/catch defensivo. Sección
  "Runbooks relacionados" sólo renderizada si `relatedRunbooks.isNotEmpty()`
  (regla anti-hallucination).
- **Wave-C C5** (941099b) — E2E test `InventoryKnowledgeFlowE2ETest` con 6
  escenarios anti-hallucination: cited returns runbooks, LOW_NO_EVIDENCE
  empty, 5xx empty (NUNCA propaga), malformed citation filtered, pod 404 +
  zero RAG calls, RAG 404 returns empty runbooks.
- **Wave-E E2** (2fe7fe3) — Audit log persistence: Flyway V2 migration,
  AuditEntry + AuditOutcome sealed (AcceptedOk/AcceptedFail/Rejected/TimedOut),
  AuditLogRepository port, JPA adapter, wire SafeOpsKernel → cada
  invocación persiste exactamente 1 entry, audit failures NO bloquean
  ejecución (log.error + continue).
- **Wave-E E3** (f3a99cb) — REST `GET /api/v1/automation/audit` paginado
  con filtros (from/to/commandKind/outcome/userId). MAX_SIZE=200 cap.
  AuditQueryService + AuditController + AuditEntryDto / AuditPageDto.
  + `GET /api/v1/automation/audit/{id}` para detalle full.
- **Wave-E E4** (7423794) — Frontend AuditLogScreen + CronjobBoardScreen
  wired al backend. AutomationClient (Ktor) + AuditPageDto/AuditEntryDto/
  AutomationRunRequest sealed con classDiscriminator="kind". OutcomeBadge
  color-coded (#00E676 primary, error, warning, tertiary). CronjobBoard
  trigger button hace POST real /api/v1/automation/run. SnackbarHost
  muestra resultado real (incluido errores raw — anti-hallucination).
- **Wave-D D1** (966950b) — Keycloak 26.6 Helm chart en `k8s/helm/keycloak/`
  con realm `apptolast` autoimportado (3 realm roles + 2 clients
  idp-frontend public PKCE + idp-backend confidential bearer-only).
  start-dev mode (D8 promociona a start). H2 file por default, Postgres
  externo opcional. IngressRoute Traefik para auth.apptolast.com con
  cert-manager. helm lint + template ok. `docs/services/keycloak.md`.
- **Wave-D D2** (a1c8bf3) — Identity OIDC Spring Security Resource Server.
  IdentityProperties (identity.oidc.enabled toggle para dev), SecurityConfig
  con jwt() + KeycloakJwtConverter, JwtPrincipalMapper (object) que mapea
  JWT → Principal de api/ con regla anti-hallucination: sin claim de roles
  → emptySet (NUNCA invents VIEWER). 8 tests JwtPrincipalMapperTest.
- **Wave-D D3** (c598c2d) — Secrets Passbolt adapter scaffold. PassboltProperties,
  PassboltApiClient (scaffold — devuelve empty hasta D4 wire real),
  StubPassboltClient (activo si url blank — NUNCA inventa secrets),
  PassboltConfig selector. 9 PassboltConfigTest. Fixes paralelos:
  `.gitignore` `secrets/` → `/secrets/` (no bloquear módulo legítimo);
  security-check hook whitelist `/platform/secrets/src/`. Módulo entra al
  repo por primera vez.
- **Wave-F P0** (ddf46b6) — Runbooks operacionales para hardening:
  `docs/runbooks/RB-50_LONGHORN_OFFSITE_BACKUP.md` (Hetzner Storage Box,
  DR drill cada 30 días, RecurringJob diario),
  `docs/runbooks/RB-51_POSTGRES_PGDUMP.md` (8 DBs inventariadas, schedule,
  DR drill mensual),
  `docs/security/network-policies.md` (matriz 11 pares allowed, apply
  procedure 1-by-1, rollback, 5 antipatterns).
- **CI/CD V** (fb98d4f) — `.github/workflows/ci.yml` extendido:
  rag-ingestor-build + rag-query-build + frontend-build (compileKotlinWasmJs
  + wasmJsBrowserDistribution artifact). helm-lint para keycloak +
  rag-ingestor + rag-query + frontend. docker-rag-ingestor +
  docker-rag-query + docker-frontend jobs con GHCR primary + DOCKERHUB
  mirror condicional. docker-cluster-watcher recibe mirror también.
  `frontend/Dockerfile` multi-stage (gradle build + nginx:1.27-alpine
  como user nginx UID 101). `frontend/nginx.conf` con MIME wasm explícito,
  gzip, SPA fallback, cache-control. `k8s/helm/frontend/` chart completo.
- **`docs/progress/2026-W20-marathon-mega-session.md`** — log de la sesión.

### Antes — Phases 2-5 skeletons + frontend (2026-05-13, branch `feat/marathon-2026-05-13`)

- **`frontend/composeApp/`** — Compose Multiplatform Web (wasmJs target) bootstrap:
  - `settings.gradle.kts` + `composeApp/build.gradle.kts` (Compose MP 1.10.2, Kotlin 2.3.21, Ktor 3.4.1, kotlinx-serialization).
  - Theme + design tokens Material 3 dark con acento `#00E676` (heredado de GreenhouseAdmin).
  - `AppNavigator` minimal sin librería externa (state holder).
  - 6 pantallas alineadas con `docs/design/specs/`:
    - `PodDashboardScreen` (Material 3 LazyColumn + phase badges + filtros).
    - `PodDetailScreen` (containers + resources + events).
    - `RagQueryScreen` (chat con citation chips).
    - `RunbookViewerScreen` (3 columnas con tree de 24 runbooks reales).
    - `CronjobBoardScreen` (grid Adaptive con 10 cronjobs de cluster-ops).
    - `LoginScreen` (placeholder Keycloak OIDC, Phase 4 dep).
  - `data/InventoryClient.kt` cliente Ktor del backend platform-app.
  - `wasmJsMain/main.kt` + `resources/index.html` con loader fallback.
- **`platform/observability/`** — Phase 2 skeleton: events (AlertFired/SloBreach), domain (Slo + SLI types), ports (AlertingUseCase, SloRepository, MetricsQueryPort), ArchUnit test, `@ApplicationModule(allowedDependencies={inventory})`.
- **`platform/secrets/`** — Phase 4 skeleton (frontend Passbolt): events sin plaintext (SecretAccessed/SecretRotated/SecretMarkedExpiring), Secret + RotationPolicy domain, `PassboltClient` port sin `getPlaintext` por diseño, ArchUnit defense-in-depth (rechaza clases `*Plaintext*` en api).
- **`platform/automation/`** — Phase 5 skeleton: Runbook con RunbookId regex-validado, ExecutionMode (INFO_ONLY/SAFE_AUTO/HUMAN_CONFIRM), RunbookExecution con StepLog, integra con cluster-ops cronjobs existentes (no los reemplaza).
- **`platform/knowledge/`** — Phase 3 skeleton: Citation con regex `[source: path#section@sha]`, Answer.Cited vs Answer.NoEvidence (anti-alucinación enforced en tipo), RagQueryUseCase con QueryOptions.minScore default 0.6. Tests: CitationTest cubre formato + extractAll + invariantes.
- **`services/rag-ingestor/`** — microservicio Phase 3: poll git cada 5 min, chunkea markdown por H2/H3 sections, embeddings + upsert a pgvector (Spring AI). Test PvcInformerQuantityTest cubre el parser de slugify + section detection (15 tests).
- **`platform/settings.gradle.kts`** activa los 5 módulos. **`platform-app/build.gradle.kts`** depende de todos.

Build verification 2026-05-13:
- `./gradlew assemble`  → BUILD SUCCESSFUL (5 modules + platform-app)
- `./gradlew check`     → BUILD SUCCESSFUL (35+ tests)
- `:inventory:test`     → 23 passing, 1 skipped (controller test bloqueado por KGP bug)

### Added — Phase 1 implementation (2026-05-13, branch `feat/marathon-2026-05-13`)

- **`platform/inventory/`** — primer bounded context activo. Hexagonal completo:
  - `api/events/InventoryEvents.kt` — `sealed interface InventoryEvent` con `PodObserved`, `ServiceObserved`, `IngressObserved`, `PvcObserved`, `CertObserved`, `ResourceDeleted`. Sin Spring, sin JPA — consumibles desde otros módulos.
  - `domain/model/` — `Pod`, `Service`, `Ingress`, `PersistentVolumeClaim`, `Certificate`, `ResourceRef`, `ResourceKind` con invariantes en `init {}`.
  - `application/port/` — `QueryInventoryUseCase` (inbound), `IngestResourceUseCase` (inbound), `InventoryRepository` (outbound), `InventoryEventPublisher` (outbound).
  - `application/service/` — `InventoryQueryService`, `InventoryIngestService` con dedup por `resourceVersion` y publicación de eventos.
  - `infrastructure/persistence/` — JPA entities con JSONB para containers/labels/ports, `JpaInventoryRepository`, `InventoryJpaMapper`.
  - `infrastructure/web/` — `InventoryController` REST + DTOs (`PodDto`, `ServiceDto`, ...).
  - `infrastructure/InventoryConfig.kt` — bean `SpringInventoryEventPublisher` (in-memory bus Fase 1).
  - `db/migration/V1__inventory_init.sql` — 5 tablas con índices y UNIQUE constraints.
  - `package-info.java` con `@ApplicationModule` para Spring Modulith.
  - Tests: `InventoryArchitectureTest` (8 reglas ArchUnit), `PodTest`, `CertificateTest`, `InventoryIngestServiceTest`, `InventoryControllerTest` (MockMvc).
- **`platform/platform-app/`** — `ModulithVerificationTest` re-habilitado (verifica boundaries del monolito con `:inventory` activo).
- **`services/cluster-watcher/`** — microservicio standalone Spring Boot:
  - `ClusterWatcherApplication.kt`, `Fabric8Config.kt`, `ClusterWatcherProperties.kt`.
  - `PodInformer`, `ServiceInformer`, `PvcInformer` con `SharedIndexInformer` + resync 30s.
  - `HttpEventPublisher` con `WebClient` + retry exponencial + timeout 5s.
  - `InformerHealthIndicator` (readiness depende de apiserver alive).
  - Test: `PvcInformerQuantityTest` (parser Quantity Ki/Mi/Gi).
  - `settings.gradle.kts` reusa el version catalog de `platform/`.
- **`k8s/helm/platform/`** — Chart completo: Deployment con Keel annotations, Service, ServiceAccount, IngressRoute Traefik + Certificate cert-manager + Middleware security-headers, NetworkPolicy ingress-only-from-traefik, `values.yaml` + `values-dev.yaml`.
- **`k8s/helm/cluster-watcher/`** — Chart con Deployment + Service + RBAC `ClusterRole`/`ClusterRoleBinding` (get/list/watch sobre pods/services/ingresses/pvcs/certificates/traefik CRDs).
- **ADR-0005** `nats-jetstream-vs-inmemory-bus.md` — decisión: in-memory bus Fase 1, NATS JetStream Fase 2. Justificación + plan de migración.
- **ADR-0006** `otel-collector-shape.md` — decisión: VictoriaMetrics + VictoriaLogs + Grafana + OTEL Collector como gateway. Presupuesto sub-500MB RAM para single-node.
- **CI extendido** `.github/workflows/ci.yml`:
  - Job `modulith-verification` re-habilitado (necesita `:inventory` que ahora existe).
  - Job `cluster-watcher-build` que reusa el wrapper de `platform/`.
  - Job `helm-lint` con `helm lint` + `helm template` para ambos charts.
  - Job `docker-cluster-watcher` para construir imagen del watcher.
  - `docker-platform` ahora mirroring a Docker Hub si `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` están configurados como secrets.

### Added — Marathon prep (2026-05-13, branch `feat/marathon-2026-05-13`)

- **`.claude/` v2 expansion** (commit `d36c5bf`): 3 agents nuevos (`team-lead`, `code-reviewer`, `mentor`), 4 skills (`validate-runbook`, `check-modulith-boundaries`, `rag-cite-or-die`, `phase-gate-check`), 3 rules (`ownership`, `citation-policy`, `modulith-rules`), 3 commands (`/phase-kickoff`, `/wave-status`, `/cite-or-die-check`), 2 hooks (`session-start.sh`, `stop-guard.sh`), `security-check.sh` ampliado con patrones DOCKERHUB_TOKEN + GitHub PAT, `settings.json` con SessionStart + Stop hooks.
- **`docs/marathon-plan.md`** (1042 líneas) — Plan maestro Waves YAML para Fases 3-7 que la routine `phase-orchestrator` parsea diariamente para arrancar PRs draft de tareas. 5 waves (C-G), 39 tasks totales, todos los 17 dolores P0-P2 del dossier mapeados a Wave F.
- **`docs/operations/`** (3 archivos): `agent-teams-runbook.md` (procedimiento operativo + recovery + fallback), `wave-A-prompts.md` (prompts copy-paste para los 8 teammates de Fase 1), `wave-B-prompts.md` (4 teammates para arranque Fase 2).
- **`docs/design/specs/`** (6 archivos): specs detalladas Material 3 / Compose MP Web para pantallas Pod Dashboard, Pod Detail, Runbook Viewer, RAG Query, Cronjob Board, Login Keycloak. Incluyen prompt copy-paste para Claude Design.
- **`docs/infrastructure/cluster-baseline-2026-05-13.md`** — snapshot completo del cluster (servidor, k8s, helm, storage, networking, BBDDs, observabilidad, cronjobs, 17 dolores) capturado en sesión 2026-05-12. Citable como `[source: docs/infrastructure/cluster-baseline-2026-05-13.md#<sección>@<sha>]`.
- **4 routines nuevas en `routines/`** (sin habilitar todavía en claude.ai):
  - `phase-orchestrator` (diario 06:07 UTC) — orquesta waves de `marathon-plan.md`
  - `runbook-migrator` (martes/viernes 02:23 UTC) — migra 27 runbooks de `cluster-ops/audit/` a `docs/runbooks/` en batches de 4
  - `citation-validator-sweep` (domingos 04:37 UTC) — barre `docs/**` aplicando `citation-policy.md`
  - `dependency-update-radar` (domingos 06:53 UTC) — compara `libs.versions.toml` con releases nuevas y abre issues agrupados

### Pending Pablo action (manual)

- Habilitar las 4 nuevas routines en https://claude.ai/code/routines copiando cada YAML al formulario (las existentes `phase-progress-report`, `pr-reviewer`, `nightly-arch-review` también siguen "Por habilitar").
- Smoke test del Agent Team con 2 teammates antes de arrancar Wave A (procedimiento en `docs/operations/agent-teams-runbook.md` §"Smoke test").
- Wave A (Fase 1) manualmente en sesión Claude Code: pegar prompts de `docs/operations/wave-A-prompts.md` al team-lead.

## [0.1.0] — 2026-05-13

### Added

- **Bootstrap Phase 0** del IDP de AppToLast. 40 archivos commiteados, CI verde.
- **Documentación maestra**: `README.md`, `CLAUDE.md`, `ARCHITECTURE.md`.
- **4 ADRs** estableciendo decisiones arquitectónicas centrales:
  - ADR-0001: Spring Modulith vs microservicios distribuidos (decisión: monolito modular + extracción selectiva)
  - ADR-0002: Compose Multiplatform Web vs React (decisión: Compose MP Web full Kotlin)
  - ADR-0003: R2R + Spring AI 1.1 como stack RAG
  - ADR-0004: Storage en git + knowledge graph en 3 capas + por qué Ubicloud no encaja
- **`.claude/` completo**: settings, 7 subagentes (architect, backend-dev, frontend-dev, qa-engineer, security-reviewer, devops-engineer, tech-writer), 3 comandos slash (`/new-module`, `/new-microservice`, `/status-report`), 3 hooks ejecutables (validate-task, security-check, format-on-save), `.mcp.json` con github + context7.
- **Gradle skeleton** multi-módulo: `libs.versions.toml` con versiones fijadas, `settings.gradle.kts`, wrapper Gradle 9.3.1 (mismo que GreenhouseAdmin).
- **Spring Boot bootstrap** mínimo: `PlatformApplication.kt` + `application.yml` + smoke test.
- **CI GitHub Actions**: build + tests + OWASP dependency check + Docker build & push a GHCR.
- **k8s/helm/platform/Chart.yaml** (esqueleto del Helm chart).
- **`docs/_template.md`** — frontmatter YAML obligatorio para todos los docs (knowledge graph declarativo).
- **`routines/`** con 3 Claude Code Routines YAML preparadas:
  - `phase-progress-report` (lunes 09:00 UTC)
  - `pr-reviewer` (GitHub pull_request.opened)
  - `nightly-arch-review` (diaria 02:00 UTC)

### Deferred to Phase 1

- **`ktlint-gradle 12.1.1`** y **`detekt 1.23.7`** quedan deshabilitados — ambos ABI-locked a versiones antiguas de Kotlin, incompatibles con Kotlin 2.3.21. TODO documentado en `platform/build.gradle.kts`.
- **`ModulithVerificationTest`** eliminado temporalmente — `ApplicationModules.of()` cambió de signature entre Spring Modulith 1.x y 2.x. Se reintroduce en Fase 1 cuando exista el primer módulo (`inventory`).
- **`modulith-verification`** job en CI comentado por la misma razón.

### Operational notes

- Branch protection en `main` habilitada tras este release: PRs obligatorios, CI verde requerido, sin force-push, sin deletions.
- Bootstrap completado en 6 commits directos a `main` (excepción documentada para repo inicialmente vacío). A partir de aquí, **todo cambio vía PR**.

[Unreleased]: https://github.com/apptolast/sistema-central-admin-servidor/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/apptolast/sistema-central-admin-servidor/releases/tag/v0.1.0
