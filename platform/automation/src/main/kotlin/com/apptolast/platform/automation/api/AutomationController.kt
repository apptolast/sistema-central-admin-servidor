package com.apptolast.platform.automation.api

import com.apptolast.platform.automation.application.port.outbound.ExecutionOutcome
import com.apptolast.platform.automation.application.service.CommandRejectedException
import com.apptolast.platform.automation.application.service.SafeOpsKernel
import com.apptolast.platform.automation.domain.model.SafeCommand
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoint público de Automation — único punto de entrada al SafeOpsKernel.
 *
 * Path namespaced bajo `/api/v1/automation/run` con autenticación obligatoria
 * (configurada por Identity Phase 4 — sólo Roles ADMIN y ONCALL pueden invocar
 * comandos no-readonly).
 *
 * Diseño:
 *  - El body usa polymorphic deserialization (kind discriminator) → SafeCommand
 *    sealed hierarchy. Cualquier kind no soportado → 400 desde Jackson.
 *  - SafeCommand.init valida shell-safe tokens → 400 si payload malformado.
 *  - Whitelist filtra kinds + namespaces → 403 si rechazado.
 *  - Executor ejecuta → 200 OK con ExecutionOutcome (exit/stdout/stderr/durMs).
 */
@RestController
@RequestMapping("/api/v1/automation")
class AutomationController(
    private val kernel: SafeOpsKernel,
) {

    @PostMapping("/run")
    fun run(@RequestBody request: AutomationRunRequest): ExecutionOutcome =
        kernel.run(request.toCommand())

    @ExceptionHandler(CommandRejectedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleRejected(e: CommandRejectedException): Map<String, Any> = mapOf(
        "error" to "REJECTED",
        "kind" to e.command.kind,
        "reason" to e.reason,
    )

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(e: IllegalArgumentException): Map<String, String> =
        mapOf("error" to (e.message ?: "invalid request"))
}

/**
 * DTO polimórfico — Jackson dispatcha por el campo `kind`.
 *
 * Ejemplo body:
 *   {"kind":"kubectl-read","verb":"get","resource":"pods","namespace":"n8n"}
 *   {"kind":"trigger-cronjob","namespace":"cluster-ops","cronJobName":"host-checks"}
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = AutomationRunRequest.KubectlReadDto::class, name = "kubectl-read"),
    JsonSubTypes.Type(value = AutomationRunRequest.HelmReadDto::class, name = "helm-read"),
    JsonSubTypes.Type(value = AutomationRunRequest.HelmRollbackDto::class, name = "helm-rollback"),
    JsonSubTypes.Type(value = AutomationRunRequest.TriggerCronJobDto::class, name = "trigger-cronjob"),
)
sealed interface AutomationRunRequest {
    fun toCommand(): SafeCommand

    data class KubectlReadDto(
        val verb: String,
        val resource: String,
        val namespace: String? = null,
        val name: String? = null,
    ) : AutomationRunRequest {
        override fun toCommand() = SafeCommand.KubectlRead(verb, resource, namespace, name)
    }

    data class HelmReadDto(
        val verb: String,
        val release: String,
        val namespace: String,
    ) : AutomationRunRequest {
        override fun toCommand() = SafeCommand.HelmRead(verb, release, namespace)
    }

    data class HelmRollbackDto(
        val release: String,
        val namespace: String,
        val revision: Int,
    ) : AutomationRunRequest {
        override fun toCommand() = SafeCommand.HelmRollback(release, namespace, revision)
    }

    data class TriggerCronJobDto(
        val namespace: String,
        val cronJobName: String,
    ) : AutomationRunRequest {
        override fun toCommand() = SafeCommand.TriggerCronJob(namespace, cronJobName)
    }
}
