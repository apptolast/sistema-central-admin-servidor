package com.apptolast.platform.automation.infrastructure.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA repository para el audit log.
 *
 * Wraping: [JpaAuditLogRepository] usa esta API para implementar el puerto
 * [com.apptolast.platform.automation.application.port.outbound.AuditLogRepository].
 * El application layer NUNCA toca este interface directamente — sólo el adapter.
 */
interface SpringDataAuditLogRepository : JpaRepository<AuditLogEntity, UUID> {

    @Query(
        """
        SELECT a FROM AuditLogEntity a
        WHERE (:from IS NULL OR a.executedAt >= :from)
          AND (:to   IS NULL OR a.executedAt < :to)
          AND (:commandKind IS NULL OR a.commandKind = :commandKind)
          AND (:outcomeLabel IS NULL OR a.outcome = :outcomeLabel)
          AND (:userId IS NULL OR a.userId = :userId)
        ORDER BY a.executedAt DESC
        """,
    )
    fun search(
        @Param("from") from: Instant?,
        @Param("to") to: Instant?,
        @Param("commandKind") commandKind: String?,
        @Param("outcomeLabel") outcomeLabel: String?,
        @Param("userId") userId: String?,
        pageable: Pageable,
    ): Page<AuditLogEntity>
}
