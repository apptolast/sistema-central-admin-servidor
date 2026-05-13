package com.apptolast.platform.automation.application.port.outbound

import com.apptolast.platform.automation.domain.model.SafeCommand
import java.time.Duration

/**
 * Puerto de salida: ejecuta un SafeCommand contra el cluster real.
 *
 * El servicio (RunbookExecutionService) decide PASS/REJECT vía Whitelist
 * antes de llamar a este puerto. La implementación (fabric8 o kubectl shell)
 * sólo recibe comandos ya validados.
 */
interface CommandExecutor {
    fun execute(command: SafeCommand, timeout: Duration = Duration.ofSeconds(30)): ExecutionOutcome
}

data class ExecutionOutcome(
    val command: SafeCommand,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
) {
    val success: Boolean get() = exitCode == 0
}
