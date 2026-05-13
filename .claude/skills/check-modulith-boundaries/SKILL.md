---
name: check-modulith-boundaries
description: >
  Ejecuta Spring Modulith Modulith.verify() + ArchUnit tests del proyecto para verificar que los
  boundaries entre módulos del monolito (inventory/secrets/observability/automation/knowledge/identity)
  siguen intactos. Falla si: domain/ importa infrastructure/, cross-module access fuera de api/,
  o si un módulo declara una dependencia no listada en su @ApplicationModule allowedDependencies.
---

# Skill: check-modulith-boundaries

Verifica que la arquitectura hexagonal + Spring Modulith de `platform/` está intacta.

## Cuándo usar

- Antes de marcar como completa cualquier tarea que toque `platform/<module>/`
- Pre-merge gate en el `code-reviewer`
- En la routine `nightly-arch-review` (ya configurada en `routines/nightly-arch-review.yaml`)
- Después de añadir un nuevo módulo o cambiar `allowedDependencies` en un `package-info.java`

## Qué verifica

### 1. ArchUnit tests del proyecto

Cada módulo en `platform/<module>/` tiene su `<Module>ModuleArchitectureTest.kt` en `src/test/kotlin/...` que valida:

- `<module>.domain.*` no importa `<module>.infrastructure.*`
- `<module>.domain.*` no importa `org.springframework.*` excepto las annotations Modulith permitidas
- `<module>.application.*` no importa `<module>.infrastructure.*` directamente (sólo via puertos en `<module>.api`)
- Naming conventions: `*Service` en `application/`, `*Repository` en `infrastructure/`, `*Event` en `api/events/`

### 2. Spring Modulith Modulith.verify()

`platform-app` tiene un test marker (cuando se reactive tras Kotlin 2.3-compatible release según commit `2f30755`):

```kotlin
@SpringBootTest(classes = [PlatformApplication::class])
class ModulithVerificationTest {

    @Test
    fun `application is a valid Spring Modulith application`() {
        val modules = ApplicationModules.of(PlatformApplication::class.java)
        modules.verify()
    }

    @Test
    fun `generate documentation snippets`() {
        val modules = ApplicationModules.of(PlatformApplication::class.java)
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases()
    }
}
```

Modulith verifica automáticamente:
- Cross-module access SOLO vía paquetes marcados como `api`
- Dependencias declaradas en `@ApplicationModule(allowedDependencies = {...})` se respetan
- No hay ciclos entre módulos
- Eventos `@DomainEvent` están bien formados

## Comando de invocación

```bash
cd platform && ./gradlew :platform-app:test --tests "*ModulithTest*" --tests "*Architecture*" --no-daemon
```

Output esperado en caso de fallo (ejemplo real Spring Modulith):

```
Module 'inventory' depends on non-exposed type
  com.apptolast.platform.observability.infrastructure.MetricRepository.

  Allowed targets: api, api.events.
```

→ Solución: refactor `observability.infrastructure.MetricRepository` para exponerlo vía `observability.api`, o eliminar la dependencia del `inventory`.

## Exit codes

- `0`: todos los boundaries OK
- `1`: al menos una violación detectada — bloquea cierre de tarea

## Limitaciones actuales

- Hasta el commit `2f30755` (fix(test): defer ModulithVerificationTest until first real module exists), el test está deferred porque platform/ no tiene módulos reales todavía
- Se reactivará automáticamente en Wave A de Fase 1 cuando `platform/inventory/` exista
- ktlint también está deferred hasta Kotlin 2.3-compatible release (commit `ae7405a`)

## Integración

Este check ya está integrado en:
- `.claude/hooks/validate-task.sh` paso 4 (condicional cuando FULL_CHECK=1)
- `routines/nightly-arch-review.yaml` paso 2
- `ci.yml` GitHub Action (cuando se añada el job arch-verify en Wave A)
