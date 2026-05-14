package com.apptolast.clusterwatcher.k8s

import com.apptolast.clusterwatcher.config.ClusterWatcherProperties
import com.apptolast.clusterwatcher.publisher.HttpEventPublisher
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Informer para Pods.
 *
 * fabric8 SharedIndexInformer:
 *   - Mantiene una cache local del estado
 *   - Recibe watch events del apiserver
 *   - Cada `resyncPeriodSeconds` re-emite el snapshot completo
 *     (mecanismo de auto-corrección si se perdieron eventos)
 *
 * Reason: en lugar de poll, watch + cache local. Latencia ~ms a cambios.
 */
@Component
class PodInformer(
    private val client: KubernetesClient,
    private val properties: ClusterWatcherProperties,
    private val publisher: HttpEventPublisher,
) {

    private val log = LoggerFactory.getLogger(PodInformer::class.java)
    private val informer = AtomicReference<SharedIndexInformer<Pod>>()

    @PostConstruct
    fun start() {
        val resyncMs = properties.resyncPeriodSeconds * 1000L

        // Phase 1: observa todos los namespaces. Filtrado por properties.namespaces
        // se aplica en el handler para no tropezar con el polimorfismo de fabric8.
        val handler = object : ResourceEventHandler<Pod> {
            override fun onAdd(pod: Pod) {
                if (shouldObserve(pod.metadata.namespace)) onChange(pod, EventKind.ADD)
            }
            override fun onUpdate(old: Pod, current: Pod) {
                if (shouldObserve(current.metadata.namespace)) onChange(current, EventKind.UPDATE)
            }
            override fun onDelete(pod: Pod, deletedFinalStateUnknown: Boolean) {
                if (shouldObserve(pod.metadata.namespace)) publishDelete(pod, deletedFinalStateUnknown)
            }
        }

        val informerInstance: SharedIndexInformer<Pod> = client.pods().inAnyNamespace()
            .inform(handler, resyncMs)

        informer.set(informerInstance)
        log.info(
            "PodInformer started resyncMs={} namespaces={}",
            resyncMs,
            properties.namespaces.ifEmpty { listOf("<all>") },
        )
    }

    private fun shouldObserve(namespace: String?): Boolean =
        properties.namespaces.isEmpty() || namespace in properties.namespaces

    @PreDestroy
    fun stop() {
        informer.get()?.stop()
        log.info("PodInformer stopped")
    }

    private enum class EventKind { ADD, UPDATE }

    private fun onChange(pod: Pod, kind: EventKind) {
        try {
            val payload = mapOf<String, Any?>(
                "kind" to "POD",
                "operation" to kind.name,
                "namespace" to pod.metadata.namespace,
                "name" to pod.metadata.name,
                "resourceVersion" to (pod.metadata.resourceVersion ?: "0"),
                "observedGeneration" to (pod.metadata.generation ?: 0L),
                "phase" to (pod.status?.phase ?: "Unknown"),
                "nodeName" to pod.spec?.nodeName,
                "podIp" to pod.status?.podIP,
                "containers" to (pod.status?.containerStatuses ?: emptyList()).map { cs ->
                    mapOf(
                        "name" to cs.name,
                        "image" to cs.image,
                        "ready" to (cs.ready ?: false),
                        "restartCount" to (cs.restartCount ?: 0),
                        "state" to when {
                            cs.state?.running != null -> "RUNNING"
                            cs.state?.waiting != null -> "WAITING"
                            cs.state?.terminated != null -> "TERMINATED"
                            else -> "WAITING"
                        },
                    )
                },
                "ownerReferenceKind" to pod.metadata.ownerReferences?.firstOrNull()?.kind,
                "ownerReferenceName" to pod.metadata.ownerReferences?.firstOrNull()?.name,
                "labels" to (pod.metadata.labels ?: emptyMap<String, String>()),
                "annotations" to (pod.metadata.annotations ?: emptyMap<String, String>()),
                "observedAt" to Instant.now().toString(),
            )
            publisher.publish(payload)
        } catch (e: Exception) {
            log.warn(
                "failed to publish pod event kind={} ns={} name={} err={}",
                kind,
                pod.metadata.namespace,
                pod.metadata.name,
                e.message,
            )
        }
    }

    internal fun publishDelete(pod: Pod, deletedFinalStateUnknown: Boolean) {
        val payload = mapOf<String, Any?>(
            "kind" to "POD",
            "operation" to "DELETE",
            "namespace" to pod.metadata.namespace,
            "name" to pod.metadata.name,
            "resourceVersion" to (pod.metadata.resourceVersion ?: "0"),
            "observedGeneration" to (pod.metadata.generation ?: 0L),
            "deletedFinalStateUnknown" to deletedFinalStateUnknown,
            "observedAt" to Instant.now().toString(),
        )
        publisher.publish(payload)
    }
}
