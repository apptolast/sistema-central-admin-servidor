package com.apptolast.platform

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Verificación de la arquitectura Spring Modulith:
 * - Boundaries entre módulos respetan `@ApplicationModule(allowedDependencies = {...})`.
 * - No hay acceso cross-module fuera de `api/`.
 * - Documentación viva (Asciidoctor) generable.
 *
 * Si este test falla, alguien rompió el contrato entre módulos.
 */
class ModulithVerificationTest {
    private val modules = ApplicationModules.of(PlatformApplication::class.java)

    @Test
    fun `modules verify`() {
        modules.verify()
    }

    @Test
    fun `print module overview`() {
        // Útil para debug: imprime el grafo de módulos detectado.
        modules.forEach { println(it) }
    }
}
