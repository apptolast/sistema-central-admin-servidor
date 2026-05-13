---
title: "ADR-0002: Compose Multiplatform Web (Wasm) vs React/Next.js/Vue para el frontend"
status: accepted
date: 2026-05-13
owner: pablo
supersedes: null
superseded-by: null
tags: [architecture, frontend, kotlin, compose]
---

# ADR-0002: Compose Multiplatform Web (Wasm) vs React/Next.js/Vue

## Status

**Accepted** — 2026-05-13.

## Context

El IDP necesita un frontend que:

1. Renderice cuadros de mando (tablas grandes, sparklines, grafos).
2. Sea modificable por una persona (Pablo) a largo plazo.
3. Refleje el "sello técnico" del owner — full Kotlin si es factible.
4. Sea defensible en una entrevista senior.
5. Reutilice patrones existentes en `AppToLast/GreenhouseAdmin` (Compose Multiplatform Web, Material 3, dark + neon green).

Opciones reales en mayo 2026:

| Stack | Pros | Contras |
|-------|------|---------|
| Vue 3 + Vite + TS + Pinia | Coherente con FICSIT.monitor. SPA moderna, tipado fuerte. | Otro idioma de sintaxis (TS/JSX). Context switching. |
| Kotlin Compose Multiplatform Web (Wasm) | Full Kotlin (sello). Mismo patrón que GreenhouseAdmin. Tipado fuerte. | Aún evoluciona; comunidad menor que React. Wasm payloads más grandes. |
| Next.js 15 + React 19 + shadcn/ui | Mercado masivo, dem# trade-off. | TS/JSX. Más volátil (cambios cada release). |
| HTMX + Thymeleaf SSR | Más simple operativamente. | Estética dated; pierde el sello técnico. |

## Decision

Adoptar **Compose Multiplatform Web 1.10.2** con target `wasmJs`, heredando los patrones de `AppToLast/GreenhouseAdmin`:

- **Tema**: Material 3 dark-first, primary `#00E676` (neon green), background `#0F1419`, surface `#1A1E23`.
- **Layout**: `AdaptiveScaffold` con 3 modos según `WindowSizeClass` (NavigationBar mobile / NavigationRail tablet / Sidebar desktop).
- **DI**: Koin 4.2.0.
- **HTTP cliente**: Ktor 3.4.x.
- **Auth**: JWT en httpOnly cookie (no localStorage).
- **i18n**: `composeResources/values/strings.xml` + `values-es/strings.xml`.
- **Deploy**: Docker multi-stage → Nginx servir Wasm distribution (igual que GreenhouseAdmin).

NO compilamos Android/iOS/Desktop en Fase 0-7. Sólo `wasmJs`. Si más adelante se necesita app móvil, el código compartido en `commonMain` está listo.

## Consequences

### Positivas

- **Full Kotlin**: backend + frontend en mismo lenguaje. Sin context switching. Refactors cross-cutting con compile-time safety.
- **Tipado fuerte hasta el navegador**: API client tipa lo que el backend devuelve (compartiendo DTOs vía `commonMain`).
- **Sello técnico defendible**: "elegí Compose MP Web porque mi platform es Kotlin de punta a punta, igual que GreenhouseAdmin".
- **Wasm rendimiento**: Compose MP en Wasm es competitivo con SPAs JS para dashboards.
- **Patrón ya validado**: GreenhouseAdmin lleva 4+ meses en producción con el mismo stack.

### Negativas

- **Payload Wasm inicial mayor** (~3-5 MB vs ~500 KB React). Mitigado con code splitting + caching Nginx agresivo.
- **Comunidad menor**: stackoverflow/twitter answers menos abundantes que React. Mitigado con KMP slack + JetBrains support.
- **Mercado laboral**: si el owner cambia de empleo, React/Next.js es más demandado. Pero el razonamiento (full Kotlin sello) sigue siendo un punto fuerte en entrevistas senior.
- **Algunos componentes JS de terceros** (visualización avanzada, grafos) requieren JS interop. Mitigado: `@JsExport` para wrappers, o `@composable` HTML fallback.

### Neutrales

- Build chain JS/npm/yarn sigue presente (Wasm necesita un loader JS); pero el código fuente es 100% Kotlin.

## Alternatives considered

1. **React 19 + Next.js 15 + shadcn/ui** — descartado: pierde el sello técnico full Kotlin y añade context switching.
2. **Vue 3 + Inertia (laravel pattern)** — descartado: el backend NO es Laravel (Spring Boot). Inertia sin Laravel pierde sentido. Vue standalone perdería coherencia con GreenhouseAdmin.
3. **HTMX + Thymeleaf SSR** — descartado: estética dated, sello técnico débil, no escala bien para dashboards interactivos complejos (charts, tables sortable, drag-drop).
4. **Compose Multiplatform DOM (no Wasm)** — descartado: en mayo 2026 Wasm target está más estable y produce mejor rendimiento que el target DOM JS de Compose MP.

## References

- [AppToLast/GreenhouseAdmin repository](https://github.com/AppToLast/GreenhouseAdmin) — patrón de referencia
- [Compose Multiplatform release notes](https://github.com/JetBrains/compose-multiplatform/releases)
- [Kotlin/Wasm production readiness](https://kotlinlang.org/docs/wasm-overview.html)
- [Material 3 Adaptive (1.2.0)](https://m3.material.io/foundations/layout/applying-layout)

## Reversal triggers

Re-evaluar este ADR si:

- Compose MP Web introduce un cambio breaking que rompe el upgrade path.
- El payload Wasm crece > 10 MB y la latencia de carga inicial impacta UX gravemente.
- El owner cambia su sello técnico (decisión personal, no de proyecto).
