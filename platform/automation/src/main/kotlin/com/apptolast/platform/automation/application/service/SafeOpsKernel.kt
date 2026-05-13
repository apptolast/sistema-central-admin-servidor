package com.apptolast.platform.automation.application.service

import com.apptolast.platform.automation.application.port.outbound.CommandExecutor
import com.apptolast.platform.automation.application.port.outbound.ExecutionOutcome
import com.apptolast.platform.automation.domain.model.SafeCommand
import com.apptolast.platform.automation.domain.model.Whitelist
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * SafeOps kernel — gate único para ejecutar SafeCommand.
 *
 * Reglas:
 *  - Todo comando pasa por `accepts()` ANTES del executor.
 *  - Rechazo → CommandRejectedException + audit log.
 *  - Aceptación → ejecuta + audit log + devuelve ExecutionOutcome.
 *
 * No es @Service para que sea instanciable en tests sin Spring. La capa de
 * infraestructura lo expone como bean en su propio @Configuration.
 */
class SafeOpsKernel(
    private val whitelist: Whitelist,
    private val executor: CommandExecutor,
) {

    private val log = LoggerFactory.getLogger(SafeOpsKernel::class.java)

    fun run(command: SafeCommand, timeout: Duration = Duration.ofSeconds(30)): ExecutionOutcome {
        when (val decision = whitelist.accepts(command)) {
            is Whitelist.Decision.Reject -> {
                log.warn("SafeOps REJECT kind={} reason={}", command.kind, decision.reason)
                throw CommandRejectedException(command, decision.reason)
            }
            is Whitelist.Decision.Accept -> {
                log.info("SafeOps ACCEPT kind={}", command.kind)
            }
        }
        val outcome = executor.execute(command, timeout)
        log.info(
            "SafeOps DONE kind={} exit={} durMs={}",
            command.kind, outcome.exitCode, outcome.durationMs,
        )
        return outcome
    }
}

class CommandRejectedException(
    val command: SafeCommand,
    val reason: String,
) : RuntimeException("Rejected ${command.kind}: $reason")
