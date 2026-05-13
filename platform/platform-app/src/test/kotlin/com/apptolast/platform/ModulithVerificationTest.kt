package com.apptolast.platform

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Verifica que los bounded contexts respetan sus límites declarados.
 *
 * DESHABILITADO temporalmente — mismo bloqueador que `InventoryControllerTest`:
 *
 *   NoSuchMethodError: ConfigDataEnvironmentPostProcessor.applyTo(...)
 *
 * Causa raíz: spring-modulith 2.0.1 fue compilado contra Spring Boot 3.4.x y
 * espera una signature de `applyTo` que cambió en 3.5.x. Importar el BOM de
 * Spring Boot lo arregla a nivel runtime, pero entonces KGP 2.3.21 falla con
 * `ClasspathEntrySnapshotter$Settings` al hacer transformación de jars de
 * `jmolecules`/`jstereotype` (dependencias transitivas de spring-modulith).
 *
 * Mitigación documentada: reactivar cuando:
 *   - Kotlin 2.3.22+ con fix al snapshot transform, O
 *   - Spring Modulith 2.0.2+ recompilado contra Spring Boot 3.5.x, O
 *   - Fallback: pin Kotlin a 2.2.x (cambio en libs.versions.toml).
 *
 * Cobertura mientras tanto: `InventoryArchitectureTest` (8 reglas ArchUnit en
 * `:inventory`) garantiza el enforcement hexagonal del único módulo activo en
 * Fase 1. Cuando llegue el 2º módulo activo (Phase 2), arreglar este blocker
 * pasa de "nice-to-have" a "P0".
 */
@Disabled("blocked: spring-modulith 2.0.1 vs Spring Boot 3.5.4 ABI skew — ver kdoc")
class ModulithVerificationTest {

    @Test
    fun `application modules respect their boundaries`() {
        ApplicationModules.of(PlatformApplication::class.java).verify()
    }
}
