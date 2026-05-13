package com.apptolast.platform.observability.application.port.inbound

import com.apptolast.platform.observability.api.events.AlertFired
import com.apptolast.platform.observability.domain.model.Slo

interface AlertingUseCase {
    fun registerSlo(slo: Slo): SloRegistration
    fun listSlos(): List<Slo>
    fun listActiveAlerts(): List<AlertFired>
    fun acknowledgeAlert(name: String, labels: Map<String, String>, ackBy: String)
}

sealed interface SloRegistration {
    data class Accepted(val name: String) : SloRegistration
    data class Rejected(val reason: String) : SloRegistration
}
