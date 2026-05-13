package com.apptolast.platform.inventory.infrastructure

import com.apptolast.platform.inventory.api.events.InventoryEvent
import com.apptolast.platform.inventory.application.port.outbound.InventoryEventPublisher
import com.apptolast.platform.inventory.infrastructure.persistence.InventoryJpaMapper
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InventoryConfig {

    @Bean
    fun inventoryJpaMapper(objectMapper: ObjectMapper): InventoryJpaMapper =
        InventoryJpaMapper(objectMapper)

    /**
     * Fase 1: publica eventos via Spring [ApplicationEventPublisher] (in-memory).
     *
     * Otros módulos consumen via `@ApplicationModuleListener` (Spring Modulith).
     * Fase 2 reemplaza este bean por `NatsJetStreamPublisher` (ver ADR-0005).
     */
    @Bean
    fun inventoryEventPublisher(
        applicationEventPublisher: ApplicationEventPublisher,
    ): InventoryEventPublisher = SpringInventoryEventPublisher(applicationEventPublisher)
}

internal class SpringInventoryEventPublisher(
    private val delegate: ApplicationEventPublisher,
) : InventoryEventPublisher {

    private val log = LoggerFactory.getLogger(SpringInventoryEventPublisher::class.java)

    override fun publish(event: InventoryEvent) {
        log.debug(
            "publishing inventory event eventId={} kind={} ns={} name={}",
            event.eventId,
            event::class.simpleName,
            event.namespace,
            event.name,
        )
        delegate.publishEvent(event)
    }
}
