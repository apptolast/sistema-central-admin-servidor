package com.apptolast.platform.automation.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Una entrada del audit log de Automation. Inmutable. Representa el resultado
 * (o intento) de ejecutar un [SafeCommand] vía SafeOpsKernel.
 *
 * Wave-E E2 obliga a que CADA llamada a `SafeOpsKernel.run()` deje exactamente
 * UNA entry aquí, sin importar el outcome (aceptado, rechazado, timeout, error).
 */
data class AuditEntry(
    val id: UUID = UUID.randomUUID(),
    val executedAt: Instant,
    val commandKind: String,
    /** JSON serializado del SafeCommand. Sin secrets — los comandos no llevan secrets. */
    val commandPayload: String,
    val executorKind: String,
    val outcome: AuditOutcome,
    val userId: String? = null,
    val correlationId: UUID? = null,
) {
    init {
        require(commandKind.isNotBlank()) { "commandKind must not be blank" }
        require(commandPayload.isNotBlank()) { "commandPayload must not be blank" }
        require(executorKind.isNotBlank()) { "executorKind must not be blank" }
    }

    /** Conveniencia para reporting: true si el comando se ejecutó con exit 0. */
    val success: Boolean get() = outcome is AuditOutcome.AcceptedOk
}

/**
 * Outcome resultante de ejecutar un SafeCommand. Sealed para garantizar
 * matching exhaustivo en queries/UI.
 */
sealed interface AuditOutcome {
    val label: String

    data class AcceptedOk(
        val exitCode: Int,
        val durationMs: Long,
        val stdoutExcerpt: String,
        val stderrExcerpt: String,
    ) : AuditOutcome {
        override val label: String = "ACCEPTED_OK"

        init {
            require(exitCode == 0) { "AcceptedOk must have exitCode=0; got $exitCode" }
            require(durationMs >= 0) { "durationMs negative: $durationMs" }
        }
    }

    data class AcceptedFail(
        val exitCode: Int,
        val durationMs: Long,
        val stdoutExcerpt: String,
        val stderrExcerpt: String,
    ) : AuditOutcome {
        override val label: String = "ACCEPTED_FAIL"

        init {
            require(exitCode != 0) { "AcceptedFail must have exitCode != 0; got $exitCode" }
            require(durationMs >= 0) { "durationMs negative: $durationMs" }
        }
    }

    data class Rejected(
        val reason: String,
    ) : AuditOutcome {
        override val label: String = "REJECTED"

        init {
            require(reason.isNotBlank()) { "rejection reason must not be blank" }
        }
    }

    data class TimedOut(
        val durationMs: Long,
        val stdoutExcerpt: String,
    ) : AuditOutcome {
        override val label: String = "TIMED_OUT"

        init {
            require(durationMs >= 0) { "durationMs negative: $durationMs" }
        }
    }

    companion object {
        /** Trunca stdout/stderr a 2000 chars para no inflar el log. */
        const val EXCERPT_MAX = 2000

        fun excerpt(s: String): String = if (s.length <= EXCERPT_MAX) s else s.take(EXCERPT_MAX)
    }
}
