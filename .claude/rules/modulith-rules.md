# Spring Modulith rules — allowedDependencies por módulo

> Espejo de las reglas hexagonal + Spring Modulith de `CLAUDE.md` §"Convenciones de código" /
> §"Arquitectura hexagonal por módulo" y `ARCHITECTURE.md` §5 (patrones transversales).
> Replicado aquí para que cada teammate las tenga accesibles sin parsear la documentación grande.

---

## Regla base hexagonal

Para cualquier módulo `platform/<module>/`:

```
api/
  ├── events/          # @DomainEvent — eventos publicados (acoplamiento async)
  ├── commands/        # Comandos que el módulo acepta vía REST/handlers (acoplamiento sync)
  └── *.kt             # Interfaces (puertos) que exponen capacidad del módulo
application/
  └── *.kt             # Casos de uso. Pueden usar Spring @Service y depender de puertos en api/
domain/
  └── *.kt             # Entidades, value objects, eventos de dominio. Puro Kotlin. SIN Spring.
infrastructure/
  └── *.kt             # Adaptadores: JPA, REST controllers, K8s clients, NATS publishers
package-info.java      # @org.springframework.modulith.ApplicationModule(displayName, allowedDependencies)
```

### Reglas inquebrantables (validadas por ArchUnit + Spring Modulith)

1. `domain/**` NO importa `infrastructure/**` (ningún módulo). Verificado por ArchUnit.
2. `domain/**` NO importa `org.springframework.*` excepto:
   - `org.springframework.modulith.events.DomainEvent` (annotation)
   - `org.springframework.modulith.events.Externalized` (annotation)
3. Cross-module access SOLO vía `api/` del módulo target. Spring Modulith `Modulith.verify()` enforce.
4. Sin ciclos entre módulos. Verificado por Modulith.
5. Eventos `@DomainEvent` sólo en `api/events/`. Publicados con `ApplicationEventPublisher`.

---

## allowedDependencies por módulo (Fase 1-7)

### `inventory` (Fase 1)

```java
@ApplicationModule(
    displayName = "Inventory",
    allowedDependencies = {}    // No depende de nada. Es el núcleo.
)
package com.apptolast.platform.inventory;
```

**Publica eventos**:
- `ResourceDiscovered` (al descubrir un pod/service/ingress/PVC/cert/dns nuevo)
- `ResourceChanged` (cambio de estado, e.g., Running → CrashLoopBackOff)
- `ResourceRemoved`

**Consume eventos** (sólo de fuentes externas, NO de otros módulos):
- `ClusterEventReceived` (publicado por `services/cluster-watcher` vía NATS JetStream cuando NATS esté operativo, mock en Wave A)

### `secrets` (Fase 4)

```java
@ApplicationModule(
    displayName = "Secrets",
    allowedDependencies = {"identity"}   // necesita saber quién pregunta (RBAC)
)
```

**Publica**:
- `SecretRotated`, `SecretExpiringSoon`

**Consume**: ninguno de otros módulos (no consume eventos cluster).

### `observability` (Fase 2)

```java
@ApplicationModule(
    displayName = "Observability",
    allowedDependencies = {"inventory"}   // necesita topología para enriquecer métricas
)
```

**Publica**:
- `AlertRaised`, `AlertResolved`

**Consume**:
- `ResourceChanged` (de inventory) — para correlacionar cambios con alertas
- `MetricThresholdCrossed` (interno; producido por adaptador VictoriaMetrics)

### `automation` (Fase 5)

```java
@ApplicationModule(
    displayName = "Automation",
    allowedDependencies = {"identity"}   // RBAC sobre quién dispara un cronjob
)
```

**Publica**: `JobScheduled`, `JobCompleted`, `JobFailed`.
**Consume**: ninguno de otros módulos.

### `knowledge` (Fase 3)

```java
@ApplicationModule(
    displayName = "Knowledge",
    allowedDependencies = {"identity"}   // RBAC sobre quién puede escribir docs
)
```

**Publica**: `DocumentPublished`, `DocumentDeprecated`, `QueryExecuted`.
**Consume**:
- `RagIndexComplete` (de `services/rag-ingestor` vía NATS) — para invalidar caches de respuestas

### `identity` (Fase 4)

```java
@ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {}   // No depende de nada. Es la raíz de la cadena de autorización.
)
```

**Publica**: `UserLoggedIn`, `PermissionGranted`.
**Consume**: ninguno.

---

## Reglas adicionales del proyecto

### Sobre `services/` (microservicios extraídos)

`services/{cluster-watcher,rag-ingestor,rag-query}` NO están sujetos a Spring Modulith — corren en JVMs separadas. Pero SÍ deben respetar hexagonal + boundaries propios:

- `services/cluster-watcher/` puede publicar a NATS pero NO debe llamar a la API REST del monolito (rompe el patrón "el cluster genera eventos, el monolito reacciona").
- `services/rag-ingestor/` puede leer pgvector (escritura) pero NO llamar a APIs del monolito en runtime — sólo procesa git polls + clusters snapshots.
- `services/rag-query/` puede ser consumido por `platform/knowledge/` vía REST (sync) o vía evento (async cuando hay query batch).

### Sobre eventos vs llamadas síncronas

**Usar evento** cuando:
- Múltiples módulos están interesados en el cambio (ej. `ResourceChanged` interesa a `observability` y a `knowledge` para reindexar)
- El productor no necesita la respuesta del consumidor
- El consumidor puede procesarlo con latencia (no real-time crítico)

**Usar llamada síncrona vía `api/`** cuando:
- Necesitas el resultado para continuar (ej. `IdentityPort.checkPermission(user, resource)`)
- Es read-only sobre estado actual (ej. `InventoryPort.getPodsByNamespace(...)`)

### Sobre `package-info.java` vs Kotlin

Spring Modulith REQUIERE `package-info.java` (Java) — Kotlin no soporta package-level annotations directamente. Cada `platform/<module>/src/main/java/com/apptolast/platform/<module>/package-info.java` contiene SOLO:

```java
@org.springframework.modulith.ApplicationModule(
    displayName = "<Name>",
    allowedDependencies = {"<dep1>", "<dep2>"}
)
package com.apptolast.platform.<module>;
```

Sin imports adicionales, sin otra cosa.

---

## Cómo el `code-reviewer` y `nightly-arch-review` validan

```bash
cd platform && ./gradlew :platform-app:test --tests "*ModulithTest*" --no-daemon
```

Si falla con `Module 'X' depends on non-exposed type Y` → 🔴 BLOCK en review. El fix es:
- Mover `Y` al `api/` del módulo target, O
- Eliminar la dependencia (usualmente vía evento), O
- Añadir el módulo target a `allowedDependencies` del source (requiere ADR si es un nuevo edge en el grafo de dependencias)

---

## Migration path (de monolith a microservice)

Si en el futuro un módulo necesita extraerse (ej. `automation` se vuelve muy CPU-intensive):

1. ADR justificando la extracción (template: `docs/adrs/000N-extract-<module>-service.md`)
2. Crear `services/<module>/` con `kotlin-facade` Spring Boot mínimo
3. Mover lógica de `platform/<module>/application/` y `infrastructure/` al servicio
4. Dejar `platform/<module>/api/` como **client SDK** que llama al servicio vía gRPC o REST
5. Externalize eventos: el módulo ya publicaba `@DomainEvent`; ahora con `@Externalized` van también a NATS para el servicio
6. Actualizar `allowedDependencies` si cambia el grafo

Ver `ARCHITECTURE.md` §2.1 y `docs/adrs/0001-spring-modulith-vs-microservices.md` para el racional.
