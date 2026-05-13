# Changelog

Todas las modificaciones notables de este proyecto se documentan aquí.

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Adherencia a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

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
