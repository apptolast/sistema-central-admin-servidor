package com.apptolast.platform.inventory.infrastructure

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * MockMvc test del controller — DESHABILITADO en Fase 1.
 *
 * Bloqueador raíz: Kotlin Gradle Plugin 2.3.21 + Gradle 9.3.1 tiene un bug en
 * el `ClasspathEntrySnapshotTransform` que falla al procesar jars transitivas
 * comunes (logback-core, log4j-api, jstereotype, jmolecules-events,
 * context-propagation, aopalliance, aspectjweaver, jakarta.transaction-api,
 * antlr4-runtime). Estas jars se pull cuando alguna dependencia del módulo
 * incluye `spring-context` o equivalente, lo cual es básicamente todo Spring.
 *
 * Síntoma:
 *   org/jetbrains/kotlin/incremental/classpathDiff/ClasspathEntrySnapshotter$Settings
 *
 * Intentos descartados:
 *   - kotlin.incremental=false                  → la transformación se registra de todas formas
 *   - Eliminar kotlin.incremental.useClasspathSnapshot deprecated → idem
 *   - Importar BOM Spring Boot                  → idem
 *   - Sacar spring-modulith-starter-test (testing.lite) → idem (falla con logback)
 *   - --rerun-tasks --no-build-cache            → idem
 *
 * El KGP 2.3.21 es pre-GA y este es un bug confirmado en su sistema de
 * snapshots para incremental compilation. Esperar a 2.3.22 (o downgrade a
 * Kotlin 2.2.x estable si Fase 2 lo necesita antes).
 *
 * Cobertura mientras tanto:
 *   - InventoryArchitectureTest (8 reglas ArchUnit) garantiza enforcement hexagonal.
 *   - InventoryIngestServiceTest cubre application layer.
 *   - PodTest / CertificateTest cubre domain layer.
 *   - El controller se ejercita en runtime en el smoke test manual de Wave A
 *     (procedimiento en docs/operations/agent-teams-runbook.md §Smoke test).
 */
@Disabled("blocked: KGP 2.3.21 ClasspathEntrySnapshotTransform bug — ver kdoc")
class InventoryControllerTest {
    @Test
    fun placeholder() {
        // intentionally empty
    }
}
