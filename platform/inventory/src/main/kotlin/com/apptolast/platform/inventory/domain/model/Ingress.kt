package com.apptolast.platform.inventory.domain.model

import java.time.Instant

data class Ingress(
    val ref: ResourceRef,
    val resourceVersion: String,
    val observedGeneration: Long,
    val kind: IngressFlavor,
    val hosts: List<String>,
    val tlsSecretName: String?,
    val observedAt: Instant,
) {
    init {
        require(ref.kind == ResourceKind.INGRESS) { "ResourceRef must have kind=INGRESS" }
        require(hosts.isNotEmpty()) { "ingress must have at least 1 host" }
    }

    fun hasTls(): Boolean = tlsSecretName != null
}

/** Distingue Ingress clásico vs IngressRoute (Traefik CRD). */
enum class IngressFlavor { K8S_INGRESS, TRAEFIK_INGRESS_ROUTE, TRAEFIK_INGRESS_ROUTE_TCP }
