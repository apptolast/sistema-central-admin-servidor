package com.apptolast.platform

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * Verifica que los bounded contexts respetan sus límites declarados.
 */
class ModulithVerificationTest {

    @Test
    fun `application modules respect their boundaries`() {
        ApplicationModules.of(PlatformApplication::class.java).verify()
    }
}
