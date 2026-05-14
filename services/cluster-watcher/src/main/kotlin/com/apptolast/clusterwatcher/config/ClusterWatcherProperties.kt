package com.apptolast.clusterwatcher.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cluster-watcher")
data class ClusterWatcherProperties(
    /** URL base del platform para publicar eventos vía HTTP (Fase 1). */
    val platformBaseUrl: String = "http://platform-apptolast-platform.platform.svc.cluster.local:80",

    /** Endpoint para POST de eventos. */
    val platformIngestEndpoint: String = "/api/v1/internal/inventory/ingest",

    /**
     * Resync period en segundos: los informers re-emiten el snapshot completo
     * cada N segundos además de los watch events incrementales. Default 300s.
     *
     * Reason: tolerancia a desincronizaciones (un evento perdido se recupera
     * en máximo `resyncPeriod` segundos).
     */
    val resyncPeriodSeconds: Long = 300,

    /**
     * Máximo de publicaciones HTTP simultáneas hacia platform.
     *
     * El snapshot inicial puede contener cientos de objetos; sin límite de
     * concurrencia, WebClient puede saturar el API y disparar timeouts falsos.
     */
    val publishConcurrency: Int = 4,

    /** Tamaño máximo de la cola local de eventos pendientes de publicar. */
    val publishQueueCapacity: Int = 2_000,

    /** Timeout por intento HTTP hacia platform. */
    val publishTimeoutSeconds: Long = 15,

    /** Reintentos adicionales después del primer intento. */
    val publishRetryAttempts: Long = 1,

    /** Namespaces a observar. Vacío = todos. */
    val namespaces: List<String> = emptyList(),

    /** Si publica eventos o solo los loggea. Útil para dev local. */
    val dryRun: Boolean = false,
)
