package com.apptolast.platform

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Verifica que los bounded contexts respetan sus límites declarados.
 *
 * Spring Modulith 2.0: si un módulo importa otro `internal` package que no
 * está en su `allowedDependencies`, este test falla en CI antes de que el
 * código llegue a `main`.
 *
 * Reactivado en Fase 1 con el primer módulo real (`:inventory`).
 *
 * Si quieres ver el grafo en formato C4/PlantUML:
 *   ./gradlew :platform-app:test --tests "*ModulithDocumentationTest"
 */
class ModulithVerificationTest {

    @Test
    fun `application modules respect their boundaries`() {
        ApplicationModules.of(PlatformApplication::class.java).verify()
    }
}
