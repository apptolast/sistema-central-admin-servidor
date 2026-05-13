package com.apptolast.platform.automation.application.port.inbound

import com.apptolast.platform.automation.application.port.outbound.AuditQuery
import com.apptolast.platform.automation.domain.model.AuditEntry
import java.util.UUID

/**
 * Puerto inbound: consulta del audit log. Lo consume el AuditController y
 * potencialmente el módulo observability (Wave-F) para correlacionar
 * alerts con comandos.
 */
interface QueryAuditUseCase {
    fun list(query: AuditQuery): List<AuditEntry>
    fun get(id: UUID): AuditEntry?
}
