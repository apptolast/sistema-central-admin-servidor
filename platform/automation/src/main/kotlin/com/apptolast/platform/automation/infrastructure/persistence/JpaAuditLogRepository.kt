package com.apptolast.platform.automation.infrastructure.persistence

import com.apptolast.platform.automation.application.port.outbound.AuditLogRepository
import com.apptolast.platform.automation.application.port.outbound.AuditQuery
import com.apptolast.platform.automation.domain.model.AuditEntry
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Adaptador JPA del puerto [AuditLogRepository]. Convierte entre el domain
 * inmutable y la entity mutable, y traduce [AuditQuery] al Spring Data query.
 *
 * Si `from` y `to` están ambos null en la query, aplica un default de
 * "últimas 24h" para evitar full-scan accidental de millones de filas.
 */
@Repository
@Transactional
class JpaAuditLogRepository(
    private val springData: SpringDataAuditLogRepository,
) : AuditLogRepository {

    override fun save(entry: AuditEntry) {
        springData.save(entry.toEntity())
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): AuditEntry? =
        springData.findById(id).map { it.toDomain() }.orElse(null)

    @Transactional(readOnly = true)
    override fun query(query: AuditQuery): List<AuditEntry> {
        val now = Instant.now()
        val effectiveTo = query.to
        val effectiveFrom = query.from
            ?: if (effectiveTo == null) now.minus(Duration.ofDays(1)) else null

        val pageable = PageRequest.of(query.page, query.size)
        return springData.findAll(
            auditSpec(
                from = effectiveFrom,
                to = effectiveTo,
                commandKind = query.commandKind,
                outcomeLabel = query.outcomeLabel,
                userId = query.userId,
            ),
            pageable,
        ).content.map { it.toDomain() }
    }

    private fun auditSpec(
        from: Instant?,
        to: Instant?,
        commandKind: String?,
        outcomeLabel: String?,
        userId: String?,
    ): Specification<AuditLogEntity> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()
        from?.let { predicates += cb.greaterThanOrEqualTo(root.get("executedAt"), it) }
        to?.let { predicates += cb.lessThan(root.get("executedAt"), it) }
        commandKind?.let { predicates += cb.equal(root.get<String>("commandKind"), it) }
        outcomeLabel?.let { predicates += cb.equal(root.get<String>("outcome"), it) }
        userId?.let { predicates += cb.equal(root.get<String>("userId"), it) }

        if (predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
    }
}
