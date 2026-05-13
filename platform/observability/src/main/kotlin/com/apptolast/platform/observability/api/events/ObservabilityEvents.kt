package com.apptolast.platform.observability.api.events

import java.time.Instant
import java.util.UUID

sealed interface ObservabilityEvent {
    val eventId: UUID
    val observedAt: Instant
}

data class AlertFired(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    val name: String,
    val severity: Severity,
    val labels: Map<String, String>,
    val value: Double,
    val threshold: Double,
    val description: String,
) : ObservabilityEvent {
    enum class Severity { INFO, WARNING, CRITICAL, EMERGENCY }
}

data class AlertResolved(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    val name: String,
    val labels: Map<String, String>,
) : ObservabilityEvent

data class SloBreach(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    val sloName: String,
    val service: String,
    val errorBudgetPercent: Double,
    val burnRate: Double,
) : ObservabilityEvent
