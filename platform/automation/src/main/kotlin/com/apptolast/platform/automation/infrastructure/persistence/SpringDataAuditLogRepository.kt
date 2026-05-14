package com.apptolast.platform.automation.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

/**
 * Spring Data JPA repository para el audit log.
 *
 * Wraping: [JpaAuditLogRepository] usa esta API para implementar el puerto
 * [com.apptolast.platform.automation.application.port.outbound.AuditLogRepository].
 * El application layer NUNCA toca este interface directamente — sólo el adapter.
 */
interface SpringDataAuditLogRepository :
    JpaRepository<AuditLogEntity, UUID>,
    JpaSpecificationExecutor<AuditLogEntity>
