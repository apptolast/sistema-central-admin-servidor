---
title: "ADR-0001: Spring Modulith vs microservicios distribuidos puros"
status: accepted
date: 2026-05-13
owner: pablo
supersedes: null
superseded-by: null
tags: [architecture, backend, kotlin, spring]
---

# ADR-0001: Spring Modulith vs microservicios distribuidos puros

## Status

**Accepted** — 2026-05-13.

## Context

El IDP debe cubrir 6 bounded contexts (inventory, secrets, observability, automation, knowledge, identity) más servicios auxiliares (RAG, cluster watcher). El owner inicial considera **microservicios distribuidos puros** porque:

1. Es el patrón "moderno" más mencionado.
2. Aporta tema técnico en una entrevista senior.
3. Permite escalar partes independientes.

Pero el contexto operacional es:

- **1 nodo Kubernetes** (Hetzner CPX62, 32 GB RAM, 16 vCPU).
- ~8 GB RAM libres tras el cluster existente.
- **Equipo de 1-2 personas** (Pablo + Alberto eventualmente).
- Single-tenant, sin requisitos de aislamiento entre tenants.
- Latencia de red intra-cluster casi cero (mismo nodo).

La evidencia 2025-2026:

- **42 % de organizaciones que adoptaron microservicios han consolidado a monolitos modulares** (CNCF Q1 2026 + casos públicos Shopify, Amazon Prime Video, Mailtrap).
- **Spring Modulith 2.0 GA** (noviembre 2025) introduce boundaries verificables (ArchUnit), event externalization, observability spans en module boundaries, y un coste operacional muy menor que N microservicios.
- Una sola JVM con 6 módulos consume ~1-2 GB vs ~6 GB para 6 JVMs separadas.

## Decision

Adoptar **modular monolith con Spring Modulith 2.0 GA** como núcleo de la plataforma, con **extracción selectiva** de 3 microservicios donde el perfil de carga lo justifica:

| Servicio extraído | Justificación |
|---|---|
| `cluster-watcher` | Long-running watch del K8s API (fabric8 informers). Reinicio aislado para no penalizar el monolito. |
| `rag-ingestor` | CPU-intensivo durante batches de embeddings. Pico predecible cada 5 min. |
| `rag-query-service` | Runtime Python (R2R nativo) + facade Kotlin Spring Boot encima. |

Los 6 módulos del monolito (inventory, secrets, observability, automation, knowledge, identity) viven en un único JAR con boundaries fuertes:

- `@ApplicationModule(displayName = "...", allowedDependencies = {...})` por módulo.
- ArchUnit fitness functions en CI: `domain/` no importa de `infrastructure/`; cross-module sólo vía `api/`.
- Spring Modulith event publication registry para eventos de dominio.
- OpenTelemetry auto-spans en module boundaries.

## Consequences

### Positivas

- **6 GB de RAM ahorrados** frente a 6 microservicios separados.
- **Refactor cross-cutting trivial**: cambiar un evento es un compile-time error en consumidores.
- **Observabilidad incluida**: Spring Modulith emite spans en cada cross-module call.
- **Testing simple**: integration tests del monolito en una sola JVM, no docker-compose multi-servicio.
- **Camino de extracción claro**: si un módulo necesita escalar, se extrae a su propio Spring Boot sin refactor mayor (el `api/` ya está aislado).
- **Defensa intelectual en entrevistas**: "Elegí modular monolith con extracción selectiva porque la evidencia 2026 (CNCF, Shopify, Modulith 2.0) muestra que en mi escala añade más fricción que valor; extraje sólo lo que el perfil de carga justifica." Razonamiento senior, no dogma.

### Negativas

- Un commit puede tocar varios módulos a la vez si las boundaries no están claras desde el principio → mitigado con ArchUnit + revisiones obligatorias.
- Un crash del monolito tira las 6 funciones a la vez → mitigado con health checks per-module, liveness/readiness diferenciada, restart rápido (~30 s con JIT cache + virtual threads).
- Menos "diferenciador técnico" superficial en una entrevista junior. Compensado por la calidad del razonamiento.

### Neutrales

- Equipo más pequeño → modular monolith encaja. Si en el futuro crece a 6+ devs con conflicting changes, se evalúa extracción.

## Alternatives considered

1. **Microservicios puros (6 JVMs)** — descartado por coste de RAM, complejidad operativa, y dolor de refactors. Evidencia 2026 muestra que es overkill a esta escala.
2. **Monolito plano (Spring Boot sin Modulith)** — descartado: pierde los boundaries verificables. ArchUnit ayuda pero Modulith añade observabilidad y event publication registry "gratis".
3. **Quarkus modular** — descartado: Spring Boot ya es el patrón del owner (Kropia, AllergenGuard), no merece la pena el cambio de ecosistema.
4. **Functions-as-a-service (Knative/OpenFaaS)** — descartado: el cluster no tiene operador FaaS instalado y no aporta para servicios stateful (inventory, secrets, identity).

## References

- [Spring Modulith 2.0 GA release blog (Nov 2025)](https://spring.io/blog/2025/11/21/spring-modulith-2-0-ga-1-4-5-and-1-3-11-released/)
- [Modular Monolith 2026 Guide & CNCF Consolidation Trend](https://dev.to/x4nent/the-modular-monolith-2026-complete-guide-spring-modulith-archunit-fitness-functions-and-lessons-from-shopify-30tb-min-architecture-878)
- [Spring Modulith Reference Documentation](https://docs.spring.io/spring-modulith/reference/)
- [Shopify's modular monolith case study](https://shopify.engineering/shopify-modular-monolith)
- [Amazon Prime Video saved 90% by consolidating microservices to monolith (Mar 2023, still relevant)](https://www.primevideotech.com/video-streaming/scaling-up-the-prime-video-audio-video-monitoring-service-and-reducing-costs-by-90)

## Reversal triggers

Re-evaluar este ADR si:

- Equipo crece a 4+ devs con frecuentes conflictos en el monolito.
- Un módulo necesita un runtime distinto (Python, Go) por razones técnicas duras.
- La latencia de un módulo crítico se ve afectada por GC pauses del resto del monolito.
- Volumen total del monolito supera 200 K LOC (improbable a corto plazo).
