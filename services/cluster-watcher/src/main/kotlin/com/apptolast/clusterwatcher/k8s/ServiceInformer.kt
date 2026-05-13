package com.apptolast.clusterwatcher.k8s

import com.apptolast.clusterwatcher.config.ClusterWatcherProperties
import com.apptolast.clusterwatcher.publisher.HttpEventPublisher
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
class ServiceInformer(
    private val client: KubernetesClient,
    private val properties: ClusterWatcherProperties,
    private val publisher: HttpEventPublisher,
) {

    private val log = LoggerFactory.getLogger(ServiceInformer::class.java)
    private val informer = AtomicReference<SharedIndexInformer<Service>>()

    @PostConstruct
    fun start() {
        val resyncMs = properties.resyncPeriodSeconds * 1000L
        val source = if (properties.namespaces.isEmpty()) {
            client.services().inAnyNamespace()
        } else {
            client.services().inNamespace(properties.namespaces.first())
        }

        val informerInstance = source.inform(
            object : ResourceEventHandler<Service> {
                override fun onAdd(svc: Service) = publish(svc, "ADD")
                override fun onUpdate(old: Service, current: Service) = publish(current, "UPDATE")
                override fun onDelete(svc: Service, dfs: Boolean) = publishDelete(svc)
            },
            resyncMs,
        )

        informer.set(informerInstance)
        log.info("ServiceInformer started resyncMs={}", resyncMs)
    }

    @PreDestroy
    fun stop() {
        informer.get()?.stop()
    }

    private fun publish(svc: Service, operation: String) {
        val payload = mapOf<String, Any?>(
            "kind" to "SERVICE",
            "operation" to operation,
            "namespace" to svc.metadata.namespace,
            "name" to svc.metadata.name,
            "resourceVersion" to (svc.metadata.resourceVersion ?: "0"),
            "observedGeneration" to (svc.metadata.generation ?: 0L),
            "type" to (svc.spec?.type ?: "ClusterIP"),
            "clusterIp" to svc.spec?.clusterIP,
            "externalIp" to svc.status?.loadBalancer?.ingress?.firstOrNull()?.ip,
            "ports" to (svc.spec?.ports ?: emptyList()).map { p ->
                mapOf(
                    "name" to p.name,
                    "protocol" to (p.protocol ?: "TCP"),
                    "port" to p.port,
                    "targetPort" to p.targetPort?.toString(),
                    "nodePort" to p.nodePort,
                )
            },
            "selector" to (svc.spec?.selector ?: emptyMap<String, String>()),
            "observedAt" to Instant.now().toString(),
        )
        publisher.publish(payload)
    }

    private fun publishDelete(svc: Service) {
        publisher.publish(
            mapOf(
                "kind" to "SERVICE",
                "operation" to "DELETE",
                "namespace" to svc.metadata.namespace,
                "name" to svc.metadata.name,
                "resourceVersion" to (svc.metadata.resourceVersion ?: "0"),
                "observedGeneration" to (svc.metadata.generation ?: 0L),
                "observedAt" to Instant.now().toString(),
            ),
        )
    }
}
