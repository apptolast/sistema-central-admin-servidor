package com.apptolast.clusterwatcher.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cluster-watcher")
data class ClusterWatcherProperties(
    /** URL base del platform para publicar eventos vía HTTP (Fase 1). */
    val platformBaseUrl: String = "http://platform:8080",

    /** Endpoint para POST de eventos. */
    val platformIngestEndpoint: String = "/api/v1/internal/inventory/ingest",

    /**
     * Resync period en segundos: los informers re-emiten el snapshot completo
     * cada N segundos además de los watch events incrementales. Default 30s.
     *
     * Reason: tolerancia a desincronizaciones (un evento perdido se recupera
     * en máximo `resyncPeriod` segundos).
     */
    val resyncPeriodSeconds: Long = 30,

    /** Namespaces a observar. Vacío = todos. */
    val namespaces: List<String> = emptyList(),

    /** Si publica eventos o solo los loggea. Útil para dev local. */
    val dryRun: Boolean = false,
)
