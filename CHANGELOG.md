# Changelog

Todas las modificaciones notables de este proyecto se documentan aquí.

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Adherencia a [Semantic Versioning](https://semver.org/lang/es/).

## [Unreleased]

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
