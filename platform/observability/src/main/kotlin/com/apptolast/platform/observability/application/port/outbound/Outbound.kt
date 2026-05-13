package com.apptolast.platform.observability.application.port.outbound

import com.apptolast.platform.observability.api.events.ObservabilityEvent
import com.apptolast.platform.observability.domain.model.Slo

interface SloRepository {
    fun save(slo: Slo)
    fun findByName(name: String): Slo?
    fun findAll(): List<Slo>
    fun delete(name: String): Boolean
}

interface ObservabilityEventPublisher {
    fun publish(event: ObservabilityEvent)
}

/**
 * Cliente OTEL Collector — consulta métricas para evaluar SLOs.
 *
 * Implementación: `infrastructure.otel.VictoriaMetricsClient` (HTTP a vmselect).
 */
interface MetricsQueryPort {
    fun executePromQL(query: String): Double
    fun executePromQLRange(query: String, lookback: java.time.Duration): List<TimePoint>

    data class TimePoint(val timestamp: java.time.Instant, val value: Double)
}
