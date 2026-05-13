package com.apptolast.platform.automation.application.service

import com.apptolast.platform.automation.application.port.inbound.QueryAuditUseCase
import com.apptolast.platform.automation.application.port.outbound.AuditLogRepository
import com.apptolast.platform.automation.application.port.outbound.AuditQuery
import com.apptolast.platform.automation.domain.model.AuditEntry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AuditQueryService(
    private val repository: AuditLogRepository,
) : QueryAuditUseCase {

    override fun list(query: AuditQuery): List<AuditEntry> = repository.query(query)

    override fun get(id: UUID): AuditEntry? = repository.findById(id)
}
