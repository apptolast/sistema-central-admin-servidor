package com.apptolast.platform.inventory.api.events

import java.time.Instant
import java.util.UUID

/**
 * Eventos públicos del módulo Inventory.
 *
 * Otros módulos del monolito pueden consumir estos eventos vía Spring Modulith
 * `@ApplicationModuleListener` o `@TransactionalEventListener`.
 *
 * Convención: cada evento captura el estado observado en un instante. No es un
 * comando ni una intención. Si el observador detecta el mismo recurso 2 veces
 * con el mismo `observedGeneration`, los consumidores deben dedupar.
 *
 * Reason: el cluster-watcher (Phase 1) re-emite el snapshot completo cada 30s
 * además de los watch events incrementales, lo que produce duplicados intencionales.
 */

/** Marker — todos los eventos de inventory implementan esta interfaz. */
sealed interface InventoryEvent {
    val eventId: UUID
    val observedAt: Instant
    val namespace: String
    val name: String
    val resourceVersion: String
    val observedGeneration: Long
}

/** Pod observado en el cluster. Puede ser un alta, modificación o re-emisión periódica. */
data class PodObserved(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val namespace: String,
    override val name: String,
    override val resourceVersion: String,
    override val observedGeneration: Long,
    val phase: String,
    val nodeName: String?,
    val podIp: String?,
    val containers: List<ContainerSnapshot>,
    val ownerReferenceKind: String?,
    val ownerReferenceName: String?,
    val labels: Map<String, String> = emptyMap(),
    val annotations: Map<String, String> = emptyMap(),
) : InventoryEvent {
    data class ContainerSnapshot(
        val name: String,
        val image: String,
        val ready: Boolean,
        val restartCount: Int,
        val state: String,
    )
}

/** Service observado en el cluster. */
data class ServiceObserved(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val namespace: String,
    override val name: String,
    override val resourceVersion: String,
    override val observedGeneration: Long,
    val type: String,
    val clusterIp: String?,
    val externalIp: String?,
    val ports: List<PortSnapshot>,
    val selector: Map<String, String> = emptyMap(),
) : InventoryEvent {
    data class PortSnapshot(
        val name: String?,
        val protocol: String,
        val port: Int,
        val targetPort: String?,
        val nodePort: Int?,
    )
}

/** Ingress observado (clásico o IngressRoute Traefik). */
data class IngressObserved(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val namespace: String,
    override val name: String,
    override val resourceVersion: String,
    override val observedGeneration: Long,
    val kind: IngressKind,
    val hosts: List<String>,
    val tlsSecretName: String?,
) : InventoryEvent {
    enum class IngressKind { K8S_INGRESS, TRAEFIK_INGRESS_ROUTE, TRAEFIK_INGRESS_ROUTE_TCP }
}

/** PersistentVolumeClaim observado. */
data class PvcObserved(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val namespace: String,
    override val name: String,
    override val resourceVersion: String,
    override val observedGeneration: Long,
    val phase: String,
    val storageClassName: String?,
    val accessModes: List<String>,
    val requestedStorageBytes: Long,
    val volumeName: String?,
) : InventoryEvent

/** Certificate de cert-manager observado. */
data class CertObserved(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val namespace: String,
    override val name: String,
    override val resourceVersion: String,
    override val observedGeneration: Long,
    val secretName: String,
    val dnsNames: List<String>,
    val issuer: String,
    val ready: Boolean,
    val expiresAt: Instant?,
) : InventoryEvent

/** Recurso eliminado (kind + namespace + name) — captura la desaparición. */
data class ResourceDeleted(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val namespace: String,
    override val name: String,
    override val resourceVersion: String,
    override val observedGeneration: Long,
    val kind: String,
) : InventoryEvent
