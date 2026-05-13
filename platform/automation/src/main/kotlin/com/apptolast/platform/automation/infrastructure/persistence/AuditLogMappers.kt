package com.apptolast.platform.automation.infrastructure.persistence

import com.apptolast.platform.automation.domain.model.AuditEntry
import com.apptolast.platform.automation.domain.model.AuditOutcome

/**
 * Mappers entre el domain [AuditEntry] y la entity JPA [AuditLogEntity].
 *
 * Las funciones son top-level porque NO necesitan estado y mantenerlas como
 * objects o classes traería ceremonia innecesaria.
 */

internal fun AuditEntry.toEntity(): AuditLogEntity = AuditLogEntity(
    id = id,
    executedAt = executedAt,
    commandKind = commandKind,
    commandPayload = commandPayload,
    executorKind = executorKind,
    outcome = outcome.label,
    exitCode = when (val o = outcome) {
        is AuditOutcome.AcceptedOk -> o.exitCode
        is AuditOutcome.AcceptedFail -> o.exitCode
        is AuditOutcome.Rejected -> null
        is AuditOutcome.TimedOut -> null
    },
    durationMs = when (val o = outcome) {
        is AuditOutcome.AcceptedOk -> o.durationMs
        is AuditOutcome.AcceptedFail -> o.durationMs
        is AuditOutcome.TimedOut -> o.durationMs
        is AuditOutcome.Rejected -> null
    },
    stdoutExcerpt = when (val o = outcome) {
        is AuditOutcome.AcceptedOk -> o.stdoutExcerpt
        is AuditOutcome.AcceptedFail -> o.stdoutExcerpt
        is AuditOutcome.TimedOut -> o.stdoutExcerpt
        is AuditOutcome.Rejected -> null
    },
    stderrExcerpt = when (val o = outcome) {
        is AuditOutcome.AcceptedOk -> o.stderrExcerpt
        is AuditOutcome.AcceptedFail -> o.stderrExcerpt
        is AuditOutcome.Rejected -> null
        is AuditOutcome.TimedOut -> null
    },
    rejectionReason = (outcome as? AuditOutcome.Rejected)?.reason,
    userId = userId,
    correlationId = correlationId,
)

internal fun AuditLogEntity.toDomain(): AuditEntry = AuditEntry(
    id = id,
    executedAt = executedAt,
    commandKind = commandKind,
    commandPayload = commandPayload,
    executorKind = executorKind,
    outcome = when (outcome) {
        "ACCEPTED_OK" -> AuditOutcome.AcceptedOk(
            exitCode = exitCode ?: 0,
            durationMs = durationMs ?: 0,
            stdoutExcerpt = stdoutExcerpt ?: "",
            stderrExcerpt = stderrExcerpt ?: "",
        )
        "ACCEPTED_FAIL" -> AuditOutcome.AcceptedFail(
            // exitCode != 0 obligatorio en domain init; si entity tiene 0 (no debería),
            // lo bumpeamos a 1 para no romper invariante. Esto es defensa contra DB corrupta.
            exitCode = (exitCode ?: 1).let { if (it == 0) 1 else it },
            durationMs = durationMs ?: 0,
            stdoutExcerpt = stdoutExcerpt ?: "",
            stderrExcerpt = stderrExcerpt ?: "",
        )
        "REJECTED" -> AuditOutcome.Rejected(
            reason = rejectionReason ?: "unknown",
        )
        "TIMED_OUT" -> AuditOutcome.TimedOut(
            durationMs = durationMs ?: 0,
            stdoutExcerpt = stdoutExcerpt ?: "",
        )
        else -> error("Unknown outcome label '$outcome' for audit id=$id")
    },
    userId = userId,
    correlationId = correlationId,
)
