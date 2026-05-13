package com.apptolast.clusterwatcher.k8s

import com.apptolast.clusterwatcher.config.ClusterWatcherProperties
import com.apptolast.clusterwatcher.publisher.HttpEventPublisher
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
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
class PvcInformer(
    private val client: KubernetesClient,
    private val properties: ClusterWatcherProperties,
    private val publisher: HttpEventPublisher,
) {

    private val log = LoggerFactory.getLogger(PvcInformer::class.java)
    private val informer = AtomicReference<SharedIndexInformer<PersistentVolumeClaim>>()

    @PostConstruct
    fun start() {
        val resyncMs = properties.resyncPeriodSeconds * 1000L

        val handler = object : ResourceEventHandler<PersistentVolumeClaim> {
            override fun onAdd(pvc: PersistentVolumeClaim) {
                if (shouldObserve(pvc.metadata.namespace)) publish(pvc, "ADD")
            }
            override fun onUpdate(old: PersistentVolumeClaim, current: PersistentVolumeClaim) {
                if (shouldObserve(current.metadata.namespace)) publish(current, "UPDATE")
            }
            override fun onDelete(pvc: PersistentVolumeClaim, dfs: Boolean) {
                if (shouldObserve(pvc.metadata.namespace)) publishDelete(pvc)
            }
        }

        val informerInstance: SharedIndexInformer<PersistentVolumeClaim> =
            client.persistentVolumeClaims().inAnyNamespace().inform(handler, resyncMs)

        informer.set(informerInstance)
        log.info("PvcInformer started resyncMs={}", resyncMs)
    }

    private fun shouldObserve(namespace: String?): Boolean =
        properties.namespaces.isEmpty() || namespace in properties.namespaces

    @PreDestroy
    fun stop() {
        informer.get()?.stop()
    }

    private fun publish(pvc: PersistentVolumeClaim, operation: String) {
        // PVC storage es Quantity ("5Gi"); para Phase 1 traducimos manualmente
        // a bytes con un parser simple. Mejor opción Phase 2: io.fabric8 Quantity.getAmountInBytes()
        val requestedBytes = pvc.spec?.resources?.requests
            ?.get("storage")?.toString()?.let(::parseQuantityToBytes) ?: 0L

        val payload = mapOf<String, Any?>(
            "kind" to "PVC",
            "operation" to operation,
            "namespace" to pvc.metadata.namespace,
            "name" to pvc.metadata.name,
            "resourceVersion" to (pvc.metadata.resourceVersion ?: "0"),
            "observedGeneration" to (pvc.metadata.generation ?: 0L),
            "phase" to (pvc.status?.phase ?: "Unknown"),
            "storageClassName" to pvc.spec?.storageClassName,
            "accessModes" to (pvc.spec?.accessModes ?: emptyList<String>()),
            "requestedStorageBytes" to requestedBytes,
            "volumeName" to pvc.spec?.volumeName,
            "observedAt" to Instant.now().toString(),
        )
        publisher.publish(payload)
    }

    private fun publishDelete(pvc: PersistentVolumeClaim) {
        publisher.publish(
            mapOf(
                "kind" to "PVC",
                "operation" to "DELETE",
                "namespace" to pvc.metadata.namespace,
                "name" to pvc.metadata.name,
                "resourceVersion" to (pvc.metadata.resourceVersion ?: "0"),
                "observedGeneration" to (pvc.metadata.generation ?: 0L),
                "observedAt" to Instant.now().toString(),
            ),
        )
    }

    /** Parser básico de k8s Quantity: 5Gi → 5368709120. Soporta Ki/Mi/Gi/Ti. */
    internal fun parseQuantityToBytes(qty: String): Long {
        val multipliers = mapOf(
            "Ki" to 1024L, "Mi" to 1024L * 1024,
            "Gi" to 1024L * 1024 * 1024, "Ti" to 1024L * 1024 * 1024 * 1024,
            "K" to 1000L, "M" to 1000L * 1000,
            "G" to 1000L * 1000 * 1000, "T" to 1000L * 1000 * 1000 * 1000,
        )
        for ((suffix, mult) in multipliers) {
            if (qty.endsWith(suffix)) {
                val numeric = qty.removeSuffix(suffix).trim().toDoubleOrNull() ?: return 0L
                return (numeric * mult).toLong()
            }
        }
        return qty.trim().toLongOrNull() ?: 0L
    }
}
