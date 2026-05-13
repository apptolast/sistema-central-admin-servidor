package com.apptolast.platform.automation.application.port.outbound

import com.apptolast.platform.automation.domain.model.AuditEntry
import java.time.Instant
import java.util.UUID

/**
 * Puerto de salida: persiste y consulta entries del audit log.
 *
 * El SafeOpsKernel **no conoce JPA** — sólo este puerto. Adaptadores:
 *  - `JpaAuditLogRepository` (producción, Postgres + Flyway V2)
 *  - `InMemoryAuditLogRepository` (tests; ver SafeOpsKernelAuditTest)
 *
 * El método `save()` debe ser idempotente respecto a `entry.id`: si la entry
 * ya existe, el segundo save NO la duplica (puede ignorar o sobreescribir;
 * el contrato sólo prohíbe duplicación de filas).
 */
interface AuditLogRepository {

    fun save(entry: AuditEntry)

    fun findById(id: UUID): AuditEntry?

    /**
     * Lista filtrada. Orden: `executed_at DESC`.
     * Los nulls en filtros = "sin restricción".
     */
    fun query(query: AuditQuery): List<AuditEntry>
}

/**
 * Parámetros de búsqueda. Si `from` y `to` son ambos null, se aplica un
 * default de "últimas 24h" en el adapter para evitar full-scan accidental.
 */
data class AuditQuery(
    val from: Instant? = null,
    val to: Instant? = null,
    val commandKind: String? = null,
    val outcomeLabel: String? = null,
    val userId: String? = null,
    val page: Int = 0,
    val size: Int = 50,
) {
    init {
        require(page >= 0) { "page must be >= 0; got $page" }
        require(size in 1..200) { "size must be 1..200; got $size" }
    }
}
