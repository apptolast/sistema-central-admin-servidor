package com.apptolast.clusterwatcher.publisher

import com.apptolast.clusterwatcher.config.ClusterWatcherProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import jakarta.annotation.PreDestroy

/**
 * Publica eventos al platform vía HTTP POST.
 *
 * Fase 1: WebClient → platform `/api/v1/internal/inventory/ingest`.
 * Fase 2: reemplazado por `NatsJetStreamPublisher` (ver ADR-0005).
 *
 * Backpressure: cola local acotada + pool fijo. Si la cola se llena, el evento
 * se descarta y el siguiente resync lo recupera.
 */
@Component
class HttpEventPublisher(
    private val properties: ClusterWatcherProperties,
    webClientBuilder: WebClient.Builder,
) {

    private val log = LoggerFactory.getLogger(HttpEventPublisher::class.java)

    private val workerId = AtomicInteger()
    private val executor = ThreadPoolExecutor(
        properties.publishConcurrency,
        properties.publishConcurrency,
        30,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(properties.publishQueueCapacity),
        ThreadFactory { runnable ->
            Thread(runnable, "cluster-watcher-publisher-${workerId.incrementAndGet()}").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )

    private val webClient: WebClient = webClientBuilder
        .baseUrl(properties.platformBaseUrl)
        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .build()

    fun publish(payload: Map<String, Any?>) {
        if (properties.dryRun) {
            log.debug("DRY-RUN event kind={} ns={} name={}", payload["kind"], payload["namespace"], payload["name"])
            return
        }

        try {
            executor.execute { publishBlocking(payload) }
        } catch (_: RejectedExecutionException) {
            log.warn(
                "publish queue full capacity={} kind={} ns={} name={} — next resync recovers",
                properties.publishQueueCapacity,
                payload["kind"],
                payload["namespace"],
                payload["name"],
            )
        }
    }

    @PreDestroy
    fun stop() {
        executor.shutdown()
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }

    private fun publishBlocking(payload: Map<String, Any?>) {
        try {
            webClient.post()
                .uri(properties.platformIngestEndpoint)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono<Void>()
                .timeout(Duration.ofSeconds(properties.publishTimeoutSeconds))
                .retry(properties.publishRetryAttempts)
                .block()
        } catch (err: Exception) {
            log.warn(
                "publish failed kind={} ns={} name={} cause={} — next resync recovers",
                payload["kind"],
                payload["namespace"],
                payload["name"],
                err.message,
            )
        }
    }
}
