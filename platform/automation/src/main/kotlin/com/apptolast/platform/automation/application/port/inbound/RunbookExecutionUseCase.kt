package com.apptolast.platform.automation.application.port.inbound

import com.apptolast.platform.automation.domain.model.Runbook
import com.apptolast.platform.automation.domain.model.RunbookId
import java.time.Instant
import java.util.UUID

interface RunbookExecutionUseCase {
    fun listRunbooks(): List<Runbook>
    fun trigger(id: RunbookId, requestedBy: String, parameters: Map<String, String>): TriggerResult
    fun getExecution(executionId: UUID): RunbookExecution?
    fun listExecutions(runbookId: RunbookId? = null, limit: Int = 50): List<RunbookExecution>
}

sealed interface TriggerResult {
    data class Accepted(val executionId: UUID) : TriggerResult
    data class RequiresConfirmation(val executionId: UUID, val description: String) : TriggerResult
    data class Rejected(val reason: String) : TriggerResult
}

data class RunbookExecution(
    val id: UUID,
    val runbookId: RunbookId,
    val requestedBy: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: Status,
    val stepLogs: List<StepLog>,
) {
    enum class Status { PENDING, RUNNING, CONFIRMED_BY_HUMAN, SUCCEEDED, FAILED, CANCELLED }

    data class StepLog(
        val stepName: String,
        val output: String,
        val exitCode: Int,
        val durationMs: Long,
    )
}
