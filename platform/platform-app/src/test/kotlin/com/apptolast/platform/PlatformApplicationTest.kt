package com.apptolast.platform

import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Smoke test mínimo de Fase 0: la clase de la aplicación es cargable.
 *
 * En Fase 1, cuando exista al menos un módulo + datasource configurable (Testcontainers),
 * este test se reemplazará por `@SpringBootTest` que verifica el arranque completo del contexto.
 */
class PlatformApplicationTest {
    @Test
    fun `main class is loadable`() {
        val clazz = PlatformApplication::class.java
        clazz shouldNotBe null
        clazz.simpleName shouldNotBe null
    }
}
