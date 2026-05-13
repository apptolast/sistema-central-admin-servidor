package com.apptolast.platform.inventory.application.port.inbound

import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.domain.model.Service

/**
 * Puerto inbound (driving): operaciones de lectura del inventario.
 *
 * Implementación: `application.service.InventoryQueryService`.
 * Adaptador inbound: `infrastructure.web.InventoryController`.
 */
interface QueryInventoryUseCase {
    fun listPods(filter: PodFilter): List<Pod>
    fun getPod(ref: ResourceRef): Pod?

    fun listServices(filter: ServiceFilter): List<Service>
    fun listIngresses(filter: IngressFilter): List<Ingress>
    fun listPvcs(filter: PvcFilter): List<PersistentVolumeClaim>
    fun listCertificates(filter: CertFilter): List<Certificate>

    /** Certs con expiración menor o igual a `thresholdDays`. */
    fun listExpiringCertificates(thresholdDays: Long): List<Certificate>
}

data class PodFilter(
    val namespace: String? = null,
    val phase: String? = null,
    val labelSelector: Map<String, String> = emptyMap(),
)

data class ServiceFilter(
    val namespace: String? = null,
    val type: String? = null,
)

data class IngressFilter(
    val namespace: String? = null,
    val host: String? = null,
)

data class PvcFilter(
    val namespace: String? = null,
    val phase: String? = null,
)

data class CertFilter(
    val namespace: String? = null,
    val ready: Boolean? = null,
)
