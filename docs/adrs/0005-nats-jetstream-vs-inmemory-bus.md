---
title: "ADR-0005: NATS JetStream vs in-memory bus para eventos de Inventory"
type: adr
owner: pablo
source-of-truth: "docs/adrs/0005-nats-jetstream-vs-inmemory-bus.md"
last-verified: 2026-05-13
tags: [adr, messaging, nats, inventory, phase-1, phase-2]
status: accepted
phase: meta
related-docs:
  - ARCHITECTURE.md
  - docs/adrs/0001-spring-modulith-vs-microservices.md
  - docs/marathon-plan.md
---

# ADR-0005 — NATS JetStream vs in-memory bus para eventos de Inventory

**Estado:** accepted
**Decisores:** Pablo Hurtado
**Fecha:** 2026-05-13

## Contexto

El módulo `inventory` recibe eventos de cambio de recursos K8s desde el servicio
`cluster-watcher`. En el cluster real ([source: docs/infrastructure/cluster-baseline-2026-05-13.md#cluster-kubernetes]) hay:

- 126 pods en 36 namespaces
- Watch events típicos: ~30/min en steady state, picos de ~200/min en rolling
- Resync periódico cada 30s → ~250 eventos por resync (126 pods + 30 ingresses
  + 30 PVCs + 40 certs + algo de services)

Necesitamos transportar esos eventos desde `cluster-watcher` (proceso JVM
extraído) al módulo `inventory` (parte del monolito Spring Modulith).

Tres opciones consideradas:

### A. HTTP POST directo (in-memory bus en el platform)

- `cluster-watcher` hace POST a `/api/v1/internal/inventory/ingest`
- El platform delega a Spring `ApplicationEventPublisher`
- Spring Modulith ya soporta `@ApplicationModuleListener` con persistencia opcional en JPA

### B. NATS JetStream (broker dedicado)

- Subject pattern `inventory.<kind>.<namespace>.<name>`
- Durability: stream con `MaxAge=24h`, replicas=1 (single node)
- Consumers: pull-based con ack manual

### C. PostgreSQL LISTEN/NOTIFY

- Insertar en una tabla `inventory_events` y emitir notify
- El platform escucha y procesa
- Spring Modulith `events-jpa` ya lo soporta

## Decisión

**Fase 1 (hoy → primer release v0.2): opción A (HTTP + in-memory bus).**
**Fase 2 (cuando hagamos extracción de microservicios o aparezca un 2.º consumer): opción B (NATS JetStream).**

**Rechazamos opción C (LISTEN/NOTIFY)** salvo que NATS no esté disponible.

## Justificación

### Por qué empezamos con A

1. **Coste cero**: ya tenemos JVM, Spring Boot y un endpoint HTTP. No hay nuevo
   componente que mantener, monitorizar o backupear.
2. **Compatible con un solo consumer**: hoy solo `inventory` consume estos eventos.
   Tener un broker para 1 consumer es overhead injustificado.
3. **Validamos contratos primero**: los eventos están definidos en
   `inventory/api/events/InventoryEvents.kt`. Si están bien diseñados, sobrevivirán
   al cambio de transport sin tocar el dominio.
4. **Recuperación en 30s**: si pierdes un evento, el siguiente resync del informer
   re-emite el snapshot completo. La ventana de inconsistencia es acotada.
5. **Latencia**: HTTP local sub-50ms es comparable a NATS local.

### Por qué pasamos a B en Fase 2

1. **Múltiples consumers**: cuando el módulo `automation` (Fase 5) y la futura UI
   (websocket subscriptions) necesiten escuchar también, NATS evita N puntos de
   acoplamiento.
2. **Durability + replay**: si el platform está caído cuando llegue un evento,
   NATS lo retiene y reentrega cuando vuelva. HTTP+in-memory lo pierde (modulado
   por resync).
3. **Backpressure real**: NATS tiene flow control. HTTP no — un `inventory` lento
   puede bloquear el `cluster-watcher`.
4. **Aislamiento de fallos**: si el platform tarda, `cluster-watcher` sigue
   publicando. Acumulación en NATS, no en el publisher.

### Por qué no C

PostgreSQL LISTEN/NOTIFY tiene limitaciones documentadas:
- Payload máximo 8KB (nuestros eventos con `labels`/`annotations` pueden superar)
- No durability cross-restart sin trampolear en una tabla
- Acopla todos los consumers al mismo Postgres (single point of failure)

Es válido como fallback de emergencia si NATS no está disponible, no como elección
de primera línea.

## Implementación Fase 1

### Endpoint HTTP en `platform`

```kotlin
@PostMapping("/api/v1/internal/inventory/ingest")
fun ingest(@RequestBody payload: IngestPayload): ResponseEntity<*> {
    // ... despacha al IngestResourceUseCase apropiado según payload.kind
}
```

Internal API (no expuesta vía Ingress — solo accesible desde el namespace via
NetworkPolicy). Auth: mTLS o token compartido (Fase 4).

### Publisher en `cluster-watcher`

Ya implementado: `HttpEventPublisher` con `WebClient` + retry 3x con backoff
exponencial (1s/2s/4s) + timeout 5s.

### Spring Modulith ApplicationEventPublisher

El `IngestResourceUseCase` publica vía Spring `ApplicationEventPublisher`. Los
listeners en otros módulos (automation, knowledge cuando existan) usan
`@ApplicationModuleListener` para procesarlos de forma asíncrona y persistente
(tabla `event_publication` gestionada por Spring Modulith JPA).

## Implementación Fase 2

### NATS JetStream config

```hcl
streams:
  - name: INVENTORY
    subjects: ["inventory.>"]
    storage: file
    replicas: 1                  # single node Hetzner
    max_age: 24h
    max_msg_size: 1MB
    discard: old
```

### Subjects

```
inventory.pod.<namespace>.<name>
inventory.service.<namespace>.<name>
inventory.ingress.<namespace>.<name>
inventory.pvc.<namespace>.<name>
inventory.cert.<namespace>.<name>
inventory.deleted.<kind>.<namespace>.<name>
```

### Migración Fase 1 → Fase 2

1. **No tocar `cluster-watcher` ni `inventory.api.events`.** Los contratos de
   eventos no cambian.
2. **Añadir bean** `NatsJetStreamPublisher : InventoryEventPublisher` que
   reemplaza a `SpringInventoryEventPublisher`.
3. **Añadir nuevo endpoint** en el `cluster-watcher` que publica a NATS además
   del POST HTTP. Dual-write durante 1 semana.
4. **Switch el primary consumer** (`inventory.ingest`) a leer de NATS.
5. **Apagar el endpoint HTTP**. Eliminar `SpringInventoryEventPublisher`.

Total: < 3 días de trabajo en Fase 2, sin downtime.

## Consecuencias

### Positivas

- Fase 1 entregable hoy mismo sin nuevo componente operacional.
- Los contratos (`InventoryEvent` data classes) son agnósticos del transport.
- Camino de migración Fase 1 → Fase 2 verificable y reversible.

### Negativas

- Fase 1 tiene un agujero de durabilidad de ~30s (entre resyncs). Esto es
  aceptable porque el dato no es crítico — el inventario reconcilia continuamente.
- Cuando llegue el 2.º consumer, hay que migrar a NATS. No hay forma de tener
  multi-consumer eficiente con HTTP-only.

### Riesgos

- **Riesgo M1**: que el `cluster-watcher` quede backloggeado si el platform
  está lento → mitigación: timeout 5s + retry + ignorar error (resync recupera).
- **Riesgo M2**: que el contrato `InventoryEvent` no aguante el upgrade a NATS
  → mitigación: evento ya tiene `eventId` UUID para dedup y `observedAt` Instant
  para ordering tolerante a reordering.

## Métricas de éxito (a verificar en Fase 1)

- p99 latencia ingest end-to-end (watch event → DB) < 500ms ([source: docs/operations/agent-teams-runbook.md#smoke-test])
- Tasa de retries en `HttpEventPublisher` < 0.5%
- Cero eventos perdidos cross-resync (medible: `resyncPeriodSeconds` * promedio
  de PRs/sec en steady state debe igualar el delta del repo en el período)

## Referencias

- Spring Modulith 2.0 docs: https://docs.spring.io/spring-modulith/reference/
- NATS JetStream: https://docs.nats.io/nats-concepts/jetstream
- fabric8 SharedInformer resync: https://fabric8io.github.io/kubernetes-client/

## Citation footer

Decisión basada en:
- Estado actual del cluster: [source: docs/infrastructure/cluster-baseline-2026-05-13.md@HEAD]
- Stack pinned: [source: platform/gradle/libs.versions.toml@HEAD] — nats 2.20.5 reservado para Fase 2
- Plan maestro: [source: docs/marathon-plan.md#wave-c@HEAD]
