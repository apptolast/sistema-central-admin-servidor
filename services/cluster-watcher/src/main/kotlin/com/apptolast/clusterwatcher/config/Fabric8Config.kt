package com.apptolast.clusterwatcher.config

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuración del cliente fabric8.
 *
 * Resolución de credenciales (fabric8 lo hace automáticamente, prioridad):
 *   1. ENV `KUBECONFIG`
 *   2. `~/.kube/config`
 *   3. ServiceAccount montado en `/var/run/secrets/kubernetes.io/serviceaccount/`
 *      (in-cluster mode — esto es lo que usamos en k8s)
 *
 * En el deployment k8s, el pod monta una ServiceAccount con RBAC mínima:
 *   - get/list/watch sobre Pods, Services, Ingresses, PVCs, Certificates,
 *     IngressRoutes en TODOS los namespaces.
 *   - NO permite create/update/delete.
 */
@Configuration
@EnableConfigurationProperties(ClusterWatcherProperties::class)
class Fabric8Config {

    private val log = LoggerFactory.getLogger(Fabric8Config::class.java)

    @Bean(destroyMethod = "close")
    fun kubernetesClient(): KubernetesClient {
        val client = KubernetesClientBuilder().build()
        log.info(
            "fabric8 client created masterUrl={} namespace={}",
            client.masterUrl,
            client.namespace ?: "<all>",
        )
        return client
    }
}
