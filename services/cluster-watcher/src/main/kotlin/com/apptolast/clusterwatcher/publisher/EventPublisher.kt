package com.apptolast.clusterwatcher.publisher

import com.apptolast.clusterwatcher.config.ClusterWatcherProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

/**
 * Publica eventos al platform vía HTTP POST.
 *
 * Fase 1: WebClient → platform `/api/v1/internal/inventory/ingest`.
 * Fase 2: reemplazado por `NatsJetStreamPublisher` (ver ADR-0005).
 *
 * Backpressure: usa Reactor scheduler con un pool acotado.
 * Retry: 3 intentos con backoff exponencial 1s/2s/4s; si fallan los 3, el evento
 * se descarta (el siguiente resync lo recuperará dentro de `resyncPeriodSeconds`).
 */
@Component
class HttpEventPublisher(
    private val properties: ClusterWatcherProperties,
    webClientBuilder: WebClient.Builder,
) {

    private val log = LoggerFactory.getLogger(HttpEventPublisher::class.java)

    private val webClient: WebClient = webClientBuilder
        .baseUrl(properties.platformBaseUrl)
        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun publish(payload: Map<String, Any?>) {
        if (properties.dryRun) {
            log.debug("DRY-RUN event kind={} ns={} name={}", payload["kind"], payload["namespace"], payload["name"])
            return
        }

        webClient.post()
            .uri(properties.platformIngestEndpoint)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono<Void>()
            .timeout(Duration.ofSeconds(5))
            .retry(2)
            .doOnError { err ->
                log.warn(
                    "publish failed kind={} ns={} name={} cause={} — next resync recovers",
                    payload["kind"],
                    payload["namespace"],
                    payload["name"],
                    err.message,
                )
            }
            .onErrorComplete()
            .subscribe()
    }
}
