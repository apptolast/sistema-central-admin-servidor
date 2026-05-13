# Changelog

Todas las modificaciones notables de este proyecto se documentan aquí.

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Adherencia a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

### Added — Phases 2-5 skeletons + frontend (2026-05-13, branch `feat/marathon-2026-05-13`)

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
