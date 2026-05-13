package com.apptolast.clusterwatcher.health

import io.fabric8.kubernetes.client.KubernetesClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

/**
 * Health indicator: readiness depende de poder hablar con el apiserver.
 *
 * Si el apiserver está caído, el pod se marca NotReady y Traefik deja de
 * enrutar tráfico hacia él. Mejor que devolver datos staled.
 */
@Component("k8sApiServer")
class InformerHealthIndicator(
    private val client: KubernetesClient,
) : HealthIndicator {

    override fun health(): Health = try {
        // Llamada barata: GET /api → lista de api groups
        val version = client.kubernetesVersion
        Health.up()
            .withDetail("apiserver", client.masterUrl)
            .withDetail("kubeVersion", "${version.major}.${version.minor}")
            .build()
    } catch (e: Exception) {
        Health.down()
            .withDetail("error", e.message ?: "unknown")
            .build()
    }
}
