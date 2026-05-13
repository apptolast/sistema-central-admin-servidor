---
description: Scaffolds a new bounded context module in the Spring Modulith monolith (platform/<name>/)
allowed-tools: Write, Edit, Bash, Read
argument-hint: <module-name>
---

Crea un nuevo bounded context module en `platform/<module-name>/` siguiendo la arquitectura hexagonal estricta del proyecto.

Pasos:

1. Validar que el nombre del módulo (`$ARGUMENTS`) es kebab-case y no existe ya en `platform/`.
2. Leer `CLAUDE.md` y `ARCHITECTURE.md` para confirmar convenciones actuales.
3. Crear la estructura de directorios:
   ```
   platform/<name>/
   ├── build.gradle.kts
   ├── src/main/kotlin/com/apptolast/platform/<name>/
   │   ├── api/                  (puertos públicos: events/, commands/)
   │   ├── application/          (casos de uso, @Service)
   │   ├── domain/               (entidades, value objects, @DomainEvent — puro Kotlin)
   │   └── infrastructure/       (REST controllers, JPA repos, K8s clients)
   ├── src/main/kotlin/com/apptolast/platform/<name>/package-info.java
   │   (@org.springframework.modulith.ApplicationModule(displayName = "...", allowedDependencies = {...}))
   ├── src/main/resources/db/migration/V<N>__<name>_init.sql
   │   (Flyway migration con esquema dedicado)
   └── src/test/kotlin/com/apptolast/platform/<name>/
       ├── <Name>ModuleArchitectureTest.kt    (ArchUnit verificando hexagonal + Modulith)
       └── (placeholders para unit + integration tests)
   ```
4. Generar archivos boilerplate:
   - `build.gradle.kts` con dependencias mínimas (spring-boot-starter, spring-modulith-events-api, etc.)
   - `package-info.java` con `@ApplicationModule(displayName = "<Name>", allowedDependencies = {...})`
   - `V<N>__<name>_init.sql` con `CREATE SCHEMA IF NOT EXISTS <name>;` y comentario placeholder
   - `<Name>ModuleArchitectureTest.kt` con tests ArchUnit:
     * domain no importa infrastructure
     * domain no importa org.springframework
     * cross-package access compliant
5. Añadir el módulo a `platform/settings.gradle.kts`: `include(":<name>")`
6. Actualizar `ARCHITECTURE.md` §3 (bounded contexts) con una fila nueva en la tabla.
7. Crear un placeholder ADR si la decisión es nueva: `docs/adrs/000N-<name>-module-design.md` con status `proposed`.
8. Ejecutar `./gradlew :<name>:build` para verificar que compila.
9. Reportar resultado al usuario con paths exactos creados.

Reglas:
- Si `$ARGUMENTS` está vacío o malformado, preguntar al usuario.
- Si el módulo entra en conflicto con uno existente, abortar con un error claro.
- NUNCA modificar otros módulos en esta operación.
- NO crear branch — el usuario lo hace.
