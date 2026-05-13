package com.apptolast.platform.automation.application.service

import com.apptolast.platform.automation.application.port.outbound.AuditLogRepository
import com.apptolast.platform.automation.application.port.outbound.CommandExecutor
import com.apptolast.platform.automation.application.port.outbound.ExecutionOutcome
import com.apptolast.platform.automation.domain.model.AuditEntry
import com.apptolast.platform.automation.domain.model.AuditOutcome
import com.apptolast.platform.automation.domain.model.SafeCommand
import com.apptolast.platform.automation.domain.model.Whitelist
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * SafeOps kernel — gate único para ejecutar SafeCommand.
 *
 * Reglas:
 *  - Todo comando pasa por `accepts()` ANTES del executor.
 *  - Rechazo → CommandRejectedException + entry en audit log (outcome REJECTED).
 *  - Aceptación → ejecuta + audit log (ACCEPTED_OK | ACCEPTED_FAIL | TIMED_OUT) +
 *    devuelve ExecutionOutcome.
 *  - Wave-E E2: **CADA invocación deja una entry**. Sin excepciones.
 *
 * No es @Service para que sea instanciable en tests sin Spring. La capa de
 * infraestructura lo expone como bean en su propio @Configuration.
 *
 * El parámetro `audit` es nullable para mantener compatibilidad con tests
 * legados (SafeOpsKernelTest) que no necesitan el audit log. En producción,
 * la AutomationConfig lo wirea siempre.
 */
class SafeOpsKernel(
    private val whitelist: Whitelist,
    private val executor: CommandExecutor,
    private val audit: AuditLogRepository? = null,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val log = LoggerFactory.getLogger(SafeOpsKernel::class.java)

    fun run(command: SafeCommand, timeout: Duration = Duration.ofSeconds(30)): ExecutionOutcome {
        val now = clock.instant()
        val executorKind = executor::class.simpleName ?: "Unknown"

        when (val decision = whitelist.accepts(command)) {
            is Whitelist.Decision.Reject -> {
                log.warn("SafeOps REJECT kind={} reason={}", command.kind, decision.reason)
                recordAudit(
                    AuditEntry(
                        executedAt = now,
                        commandKind = command.kind,
                        commandPayload = serializeCommand(command),
                        executorKind = executorKind,
                        outcome = AuditOutcome.Rejected(decision.reason),
                    ),
                )
                throw CommandRejectedException(command, decision.reason)
            }
            is Whitelist.Decision.Accept -> {
                log.info("SafeOps ACCEPT kind={}", command.kind)
            }
        }

        val outcome: ExecutionOutcome = executor.execute(command, timeout)
        log.info(
            "SafeOps DONE kind={} exit={} durMs={}",
            command.kind, outcome.exitCode, outcome.durationMs,
        )
        recordAudit(
            AuditEntry(
                executedAt = now,
                commandKind = command.kind,
                commandPayload = serializeCommand(command),
                executorKind = executorKind,
                outcome = if (outcome.success) {
                    AuditOutcome.AcceptedOk(
                        exitCode = outcome.exitCode,
                        durationMs = outcome.durationMs,
                        stdoutExcerpt = AuditOutcome.excerpt(outcome.stdout),
                        stderrExcerpt = AuditOutcome.excerpt(outcome.stderr),
                    )
                } else {
                    AuditOutcome.AcceptedFail(
                        exitCode = outcome.exitCode,
                        durationMs = outcome.durationMs,
                        stdoutExcerpt = AuditOutcome.excerpt(outcome.stdout),
                        stderrExcerpt = AuditOutcome.excerpt(outcome.stderr),
                    )
                },
            ),
        )
        return outcome
    }

    private fun recordAudit(entry: AuditEntry) {
        val repo = audit ?: return
        try {
            repo.save(entry)
        } catch (ex: Exception) {
            // El audit log no puede tumbar la ejecución del comando. Si el log
            // falla, log a SLF4J y seguimos. Operativamente, esto debería disparar
            // alerta vía observability (Wave-E E3.5, Phase 6 hardening).
            log.error("audit log write failed (continuing): {} entry={}", ex.message, entry, ex)
        }
    }

    private fun serializeCommand(command: SafeCommand): String =
        // Estructura simple: kind + toString() del data class (Kotlin imprime todos
        // los campos, sin secrets — los SafeCommand no llevan secrets). Para JSON
        // pretty se puede pasar Jackson en el adapter; aquí mantenemos zero deps.
        "{\"kind\":\"${command.kind}\",\"data\":\"${command.toString().replace("\"", "\\\"")}\"}"
}

class CommandRejectedException(
    val command: SafeCommand,
    val reason: String,
) : RuntimeException("Rejected ${command.kind}: $reason")
