---
title: "ADR-0007: Knowledge-aware Inventory — cross-module port import"
type: adr
owner: pablo
source-of-truth: "docs/adrs/0007-knowledge-aware-inventory.md"
last-verified: 2026-05-13
tags: [adr, knowledge, inventory, rag, anti-hallucination, phase-3]
status: accepted
phase: 3
related-docs:
  - ARCHITECTURE.md
  - docs/adrs/0001-spring-modulith-vs-microservices.md
  - docs/adrs/0003-r2r-rag-stack.md
  - docs/operations/wave-C-prompts.md
---

# ADR-0007 — Knowledge-aware Inventory

**Estado:** accepted
**Decisores:** Pablo Hurtado
**Fecha:** 2026-05-13

## Contexto

Cerrando el loop de la Fase 3 (Knowledge/RAG). El módulo `platform/knowledge`
expone `QueryKnowledgePort` (`application.port.inbound.QueryKnowledgePort`)
que recupera `List<Citation>` para una pregunta en texto libre, con regla
**anti-hallucination** [[feedback_rag_anti_hallucination]]: devuelve `emptyList`
en cualquier fallo en vez de inventar runbooks.

Wave-C prompt C3 (`docs/operations/wave-C-prompts.md#C3`) pide enriquecer
el detalle de un Pod con los runbooks relevantes del repositorio, para que el
operador que abre el detalle de `postgres-n8n-0` vea inmediatamente
`RB-10_PG_CONNECTIONS_HIGH`, `RB-13_PG_TXID_WRAPAROUND`, etc.

Dos opciones de wiring:

### A. Inventory importa knowledge.QueryKnowledgePort

- `inventory/build.gradle.kts` declara `implementation(project(":knowledge"))`.
- `InventoryQueryService` recibe `QueryKnowledgePort?` por constructor.
- `application.port.inbound.PodDetail` (nuevo) compone `Pod` + `List<Citation>`.
- Citation es un *value type estable* de `knowledge.domain.model`, parte del
  contrato público del bounded context Knowledge.

### B. Cluster-watcher hace fan-out asíncrono y populariza una columna

- Cada vez que se actualiza un Pod, un consumidor escucha el evento y resuelve
  los runbooks vía knowledge → escribe a tabla `inventory.pod_runbook`.
- Lecturas baratas (sin llamada cross-service), pero stale.

### C. Frontend hace dos calls

- `GET /api/v1/inventory/pods/{ns}/{name}` devuelve solo Pod.
- `POST /api/v1/rag/query` por separado para runbooks.
- Backend desacoplado pero loops el knowledge port en cliente.

## Decisión

**Opción A.** Inventory importa `QueryKnowledgePort` directamente.

## Justificación

1. **Anti-hallucination by design.** `QueryKnowledgePort.query()` *nunca lanza*
   por contrato; cualquier fallo cae a `emptyList`. Inventory añade UNA capa
   más de `try/catch` en `findRunbooksFor()` por defensa en profundidad, pero
   no es estrictamente necesaria — y es testeada explícitamente
   (`InventoryPodDetailServiceTest#getPodDetail returns empty runbooks when knowledge throws`).

2. **Single-node operativo.** Opción B (fan-out asíncrono) requiere NATS o
   columna materializada que se mantenga al día. En el cluster actual hay
   126 pods y los runbooks cambian con baja frecuencia (~1-2/semana). Una
   query síncrona de 200-500ms a `rag-query.platform.svc:8082` es trivialmente
   barata vs la operativa de fan-out + invalidation.

3. **Cross-module domain dep es legítima.** Spring Modulith permite
   dependencias hacia el *paquete público* de otros módulos. `Citation` vive
   en `knowledge.domain.model` (no en `infrastructure`) y es:
   - Inmutable (`data class` + `init` validations).
   - Sin dependencias externas (no Spring, no JPA).
   - Estable: cambios romperían el contrato RAG global, así que su evolución
     ya está protegida.
   La regla "inventory.application no depende de knowledge.infrastructure"
   sigue vigente — y verificada por ArchUnit existente
   (`InventoryArchitectureTest#application layer must not depend on JPA, web or fabric8`).

4. **Graceful degradation en dev local.** El constructor inyecta
   `QueryKnowledgePort?`, no `QueryKnowledgePort`. Si en dev no hay rag-query
   desplegado y Spring no inyecta el bean, `getPodDetail` devuelve
   `PodDetail(pod, relatedRunbooks=emptyList)`. Cobertura del caso en
   `InventoryPodDetailServiceTest#getPodDetail returns empty runbooks when no knowledge port wired`.

5. **Coste cognitivo bajo.** El operador que abre un Pod NO tiene que
   recordar que existe un knowledge module. El endpoint sigue siendo
   `GET /api/v1/inventory/pods/{ns}/{name}`, simplemente más rico.

## Implicaciones operativas

- **Latencia del endpoint detail.** Antes ~10ms (DB local). Después ~200-500ms
  por la llamada HTTP a rag-query con timeouts agresivos (connect 500ms,
  read 2s — ver `KnowledgeProperties`). Aceptable: el detail es una acción
  manual del operador, no un endpoint de high-throughput.

- **DTO contract change.** `GET /api/v1/inventory/pods/{ns}/{name}` ahora
  devuelve `PodDetailDto` (no `PodDto`). El frontend
  (`PodDetailScreen.kt` en Wave-C C4) debe renderizar la nueva sección
  "Runbooks relacionados" sólo cuando `relatedRunbooks.isNotEmpty()`.
  Backwards compatibility: el JSON `PodDetailDto.pod` contiene los mismos
  campos que `PodDto` (es el wrapping), así que clientes existentes que
  hagan `.pod.name` siguen funcionando.

- **Observabilidad.** Cuando knowledge falla, se logguea `log.warn` desde
  `InventoryQueryService.findRunbooksFor`. Si el porcentaje de runbooks
  vacíos sube en producción, mirar `rag-query` y `rag-ingestor` antes de
  asumir que el corpus no cubre el caso.

## Alternativas descartadas

- **Opción B (fan-out asíncrono):** sobre-diseño para un cluster single-node
  con ~126 pods. Re-evaluar si en Fase 7 el grafo de relaciones (Cognee
  topology) crece a >1000 entidades.

- **Opción C (frontend hace dos calls):** acopla la regla anti-hallucination
  al cliente — pero queremos que esa regla viva en el backend para que
  CUALQUIER consumer (UI, CLI, API externa, agent autónomo) la herede.

## Validación

- **Tests:** `:inventory:test` 23 tests verdes incluyendo 5 nuevos en
  `InventoryPodDetailServiceTest` que cubren los 5 escenarios anti-hallucination.
- **ArchUnit:** 8 reglas siguen pasando. No se relaja ninguna; la dep
  cross-module no viola ninguna regla porque importa sólo
  `knowledge.application.port.inbound` y `knowledge.domain.model` (público).
- **Build:** `./gradlew :inventory:build` ok.

## Citación

- Implementación: `platform/inventory/src/main/kotlin/com/apptolast/platform/inventory/application/service/InventoryQueryService.kt`
- Port: `platform/knowledge/src/main/kotlin/com/apptolast/platform/knowledge/application/port/inbound/QueryKnowledgePort.kt`
- Tests: `platform/inventory/src/test/kotlin/com/apptolast/platform/inventory/application/InventoryPodDetailServiceTest.kt`
- Wave-C plan: `docs/operations/wave-C-prompts.md#C3`
- Anti-hallucination policy: [[feedback_rag_anti_hallucination]] (memoria)
