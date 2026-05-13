package com.apptolast.platform.automation.api

import com.apptolast.platform.automation.application.port.inbound.QueryAuditUseCase
import com.apptolast.platform.automation.application.port.outbound.AuditQuery
import com.apptolast.platform.automation.domain.model.AuditEntry
import com.apptolast.platform.automation.domain.model.AuditOutcome
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * REST API del audit log (Wave-E E3).
 *
 * Endpoints:
 *  - GET /api/v1/automation/audit       — listado paginado con filtros
 *  - GET /api/v1/automation/audit/{id}  — detalle completo (stdout/stderr full)
 *
 * El listado NO incluye stdout/stderr completos por defecto — solo excerpts
 * (2000 chars). Usar el detail endpoint para inspección forense.
 */
@RestController
@RequestMapping("/api/v1/automation/audit")
class AuditController(
    private val useCase: QueryAuditUseCase,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) commandKind: String?,
        @RequestParam(required = false) outcome: String?,
        @RequestParam(required = false) userId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): AuditPageDto {
        val cappedSize = size.coerceAtMost(MAX_SIZE)
        val query = AuditQuery(
            from = from,
            to = to,
            commandKind = commandKind?.takeIf { it.isNotBlank() },
            outcomeLabel = outcome?.takeIf { it.isNotBlank() },
            userId = userId?.takeIf { it.isNotBlank() },
            page = page.coerceAtLeast(0),
            size = cappedSize,
        )
        val entries = useCase.list(query)
        return AuditPageDto(
            page = query.page,
            size = query.size,
            items = entries.map(AuditEntryDto::summaryFrom),
        )
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<AuditEntryDto> {
        val entry = useCase.get(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(AuditEntryDto.fullFrom(entry))
    }

    companion object {
        // Cap defensivo. El cliente puede pedir más, lo recortamos silenciosamente
        // a este número. Coincide con el cap del port AuditQuery (200).
        const val MAX_SIZE = 200
    }
}

data class AuditPageDto(
    val page: Int,
    val size: Int,
    val items: List<AuditEntryDto>,
)

/**
 * DTO de un audit entry. Tiene 2 factory methods según contexto:
 *  - [summaryFrom]: en listados — stdout/stderr son los excerpts del domain
 *    (ya truncados a 2000 chars).
 *  - [fullFrom]: en detail — idénticos a summary porque el domain ya almacena
 *    sólo excerpts. Si en Phase 6 añadimos blob storage para outputs largos,
 *    fullFrom se diferenciaría haciendo fetch del blob.
 */
data class AuditEntryDto(
    val id: UUID,
    val executedAt: String,
    val commandKind: String,
    val commandPayload: String,
    val executorKind: String,
    val outcome: String,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
    val stdoutExcerpt: String? = null,
    val stderrExcerpt: String? = null,
    val rejectionReason: String? = null,
    val userId: String? = null,
    val correlationId: String? = null,
) {
    companion object {
        fun summaryFrom(entry: AuditEntry): AuditEntryDto = build(entry, includeStreams = true)
        fun fullFrom(entry: AuditEntry): AuditEntryDto = build(entry, includeStreams = true)

        private fun build(entry: AuditEntry, includeStreams: Boolean): AuditEntryDto {
            val o = entry.outcome
            return AuditEntryDto(
                id = entry.id,
                executedAt = entry.executedAt.toString(),
                commandKind = entry.commandKind,
                commandPayload = entry.commandPayload,
                executorKind = entry.executorKind,
                outcome = o.label,
                exitCode = when (o) {
                    is AuditOutcome.AcceptedOk -> o.exitCode
                    is AuditOutcome.AcceptedFail -> o.exitCode
                    else -> null
                },
                durationMs = when (o) {
                    is AuditOutcome.AcceptedOk -> o.durationMs
                    is AuditOutcome.AcceptedFail -> o.durationMs
                    is AuditOutcome.TimedOut -> o.durationMs
                    is AuditOutcome.Rejected -> null
                },
                stdoutExcerpt = if (includeStreams) {
                    when (o) {
                        is AuditOutcome.AcceptedOk -> o.stdoutExcerpt
                        is AuditOutcome.AcceptedFail -> o.stdoutExcerpt
                        is AuditOutcome.TimedOut -> o.stdoutExcerpt
                        is AuditOutcome.Rejected -> null
                    }
                } else null,
                stderrExcerpt = if (includeStreams) {
                    when (o) {
                        is AuditOutcome.AcceptedOk -> o.stderrExcerpt
                        is AuditOutcome.AcceptedFail -> o.stderrExcerpt
                        else -> null
                    }
                } else null,
                rejectionReason = (o as? AuditOutcome.Rejected)?.reason,
                userId = entry.userId,
                correlationId = entry.correlationId?.toString(),
            )
        }
    }
}
