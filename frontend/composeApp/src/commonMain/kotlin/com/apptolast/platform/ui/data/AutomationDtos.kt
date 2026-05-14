package com.apptolast.platform.ui.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs cliente para /api/v1/automation/[recurso] — espejos de los DTOs Kotlin del
 * backend (platform/automation/api/AuditController.kt + AutomationController.kt).
 */

@Serializable
data class AuditPageDto(
    val page: Int,
    val size: Int,
    val items: List<AuditEntryDto> = emptyList(),
)

@Serializable
data class AuditEntryDto(
    val id: String,
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
)

@Serializable
data class CronJobDto(
    val namespace: String,
    val name: String,
    val schedule: String,
    val suspended: Boolean = false,
    val activeJobs: Int = 0,
    val lastScheduleTime: String? = null,
    val lastSuccessfulTime: String? = null,
    val status: String,
)

/**
 * Payload para POST /api/v1/automation/run. Jackson en el backend
 * dispatchea por `kind` a la sealed hierarchy SafeCommand. Sólo modelamos
 * aquí los kinds que la UI dispara hoy.
 */
@Serializable
sealed interface AutomationRunRequest {
    @Serializable
    @SerialName("kubectl-read")
    data class KubectlReadDto(
        val verb: String,
        val resource: String,
        val namespace: String? = null,
        val name: String? = null,
    ) : AutomationRunRequest

    @Serializable
    @SerialName("trigger-cronjob")
    data class TriggerCronJobDto(
        val namespace: String,
        val cronJobName: String,
    ) : AutomationRunRequest
}

@Serializable
data class ExecutionOutcomeDto(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
)
