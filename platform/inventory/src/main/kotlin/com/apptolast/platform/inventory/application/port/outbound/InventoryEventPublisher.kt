package com.apptolast.platform.inventory.application.port.outbound

import com.apptolast.platform.inventory.api.events.InventoryEvent

/**
 * Puerto outbound (driven): publica eventos hacia otros módulos del monolito
 * y, en Fase 2, hacia NATS JetStream para consumidores externos.
 *
 * Implementación Fase 1: `infrastructure.events.SpringApplicationEventPublisher`
 * que delega en Spring `ApplicationEventPublisher`.
 *
 * Implementación Fase 2: `NatsJetStreamPublisher` (ver ADR-0005).
 */
interface InventoryEventPublisher {
    fun publish(event: InventoryEvent)
}
