package com.apptolast.clusterwatcher

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Bootstrap del servicio cluster-watcher.
 *
 * Observa el cluster K8s vía fabric8 SharedInformer y publica eventos de
 * cambio al platform (Fase 1: HTTP POST in-memory bus, Fase 2: NATS JetStream).
 *
 * NO comparte JVM con platform-app; corre como deployment separado en k8s.
 *
 * Health checks:
 *   - GET /actuator/health/liveness  → JVM viva
 *   - GET /actuator/health/readiness → informers conectados al apiserver
 */
@SpringBootApplication
class ClusterWatcherApplication

fun main(args: Array<String>) {
    runApplication<ClusterWatcherApplication>(*args)
}
