package com.apptolast.platform.inventory.infrastructure

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * MockMvc test del controller — DESHABILITADO temporalmente.
 *
 * Bloqueador: con el setup actual de `:inventory` (sin importar el BOM de
 * Spring Boot), spring-modulith-starter-test arrastra spring-core 6.1.x mientras
 * que spring-boot-starter-test arrastra spring-core 6.2.x, produciendo:
 *
 *     NoClassDefFoundError: org/springframework/util/MimeType$SpecificityComparator
 *
 * Importar el BOM lo resuelve, pero entonces Kotlin Gradle Plugin 2.3.21 falla
 * con `ClasspathEntrySnapshotter$Settings` ClassNotFound al procesar jars de
 * jmolecules/jstereotype/spring-modulith (incompatibilidad KGP 2.3.21 con
 * algunas formas de jar).
 *
 * Mitigación documentada en Phase 2 (waveB):
 *   - Reactivar cuando se publique Kotlin 2.3.22+ con fix al snapshot transform.
 *   - O bien volver a Kotlin 2.2.x estable (cambio en libs.versions.toml).
 *
 * Cobertura mientras tanto:
 *   - InventoryArchitectureTest (8 reglas) garantiza que el controller vive en
 *     `infrastructure.web` y no acopla otras capas.
 *   - InventoryIngestServiceTest cubre la lógica de application.
 *   - PodTest / CertificateTest cubre el dominio.
 *   - Integration test E2E con Testcontainers se añade en Wave A (Phase 1 final).
 */
@Disabled("blocked: KGP 2.3.21 snapshot transform + spring-core version skew — ver kdoc")
class InventoryControllerTest {
    @Test
    fun placeholder() {
        // intentionally empty
    }
}
