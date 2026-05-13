package com.apptolast.platform.automation.domain.model

import java.time.Instant

/**
 * Modelo de runbook ejecutable.
 *
 * Reason de diseño: cada runbook tiene un ID estable (RB-XX) que corresponde
 * al filename en `cluster-ops/audit/RUNBOOKS/`. La plataforma renderiza la
 * doc en RunbookViewer y opcionalmente ejecuta los pasos automáticos.
 *
 * Hay 2 modos de ejecución:
 *  - INFO_ONLY: el runbook sólo se lee (no hay automatización).
 *  - SAFE_AUTO: pasos automatizables que NO destruyen estado (ej. dump pod logs).
 *  - HUMAN_CONFIRM: requiere confirmación humana antes de cada paso destructivo.
 */
data class Runbook(
    val id: RunbookId,
    val title: String,
    val severity: Severity,
    val mode: ExecutionMode,
    val triggers: List<RunbookTrigger>,
    val createdAt: Instant,
    val lastVerifiedAt: Instant?,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }

    enum class Severity { P0, P1, P2, P3 }
    enum class ExecutionMode { INFO_ONLY, SAFE_AUTO, HUMAN_CONFIRM }
}

@JvmInline
value class RunbookId(val value: String) {
    init {
        require(value.matches(Regex("^[A-Z]{2,}[-_][A-Z0-9_-]+$"))) {
            "RunbookId must look like RB-01_HOST_DISK_HIGH; got $value"
        }
    }
}

/**
 * Define cuándo y por qué se sugiere ejecutar el runbook.
 */
sealed interface RunbookTrigger {
    /** Disparado por una alerta concreta (PrometheusAlertName). */
    data class OnAlert(val alertName: String) : RunbookTrigger

    /** Cronjob programado (ej. mensual cleanup). */
    data class OnSchedule(val cron: String) : RunbookTrigger

    /** Triggered manualmente por un humano desde la UI. */
    data object Manual : RunbookTrigger
}
