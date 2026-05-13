package com.apptolast.platform.observability.domain.model

import java.time.Duration

/**
 * Service Level Objective.
 *
 * Modelo siguiendo Google SRE Workbook: SLI + objetivo + ventana de tiempo.
 *
 * Example:
 *   SLI: http_request_duration_seconds_p99 < 0.5s
 *   Objetivo: 99.5%
 *   Ventana: 30 días rolling
 */
data class Slo(
    val name: String,
    val service: String,
    val sli: ServiceLevelIndicator,
    val objectivePercent: Double,
    val window: Duration,
) {
    init {
        require(name.isNotBlank())
        require(service.isNotBlank())
        require(objectivePercent in 0.0..100.0) { "objective must be 0..100, got $objectivePercent" }
        require(!window.isNegative && !window.isZero) { "window must be positive" }
    }

    fun errorBudget(): Double = 100.0 - objectivePercent
}

sealed interface ServiceLevelIndicator {
    /** Latency p99 < threshold seconds. */
    data class LatencyP99(val thresholdSeconds: Double) : ServiceLevelIndicator

    /** Success rate (2xx + 3xx) / total >= objective. */
    data object SuccessRate : ServiceLevelIndicator

    /** Custom PromQL expression resulting in 0..1 ratio. */
    data class PromQLRatio(val numerator: String, val denominator: String) : ServiceLevelIndicator
}
