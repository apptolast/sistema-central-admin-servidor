package com.apptolast.platform.inventory.application.port.outbound

import com.apptolast.platform.inventory.application.port.inbound.CertFilter
import com.apptolast.platform.inventory.application.port.inbound.IngressFilter
import com.apptolast.platform.inventory.application.port.inbound.PodFilter
import com.apptolast.platform.inventory.application.port.inbound.PvcFilter
import com.apptolast.platform.inventory.application.port.inbound.ServiceFilter
import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.domain.model.Service

/**
 * Puerto outbound (driven): persistencia del inventario.
 *
 * Implementación: `infrastructure.persistence.JpaInventoryRepository`.
 *
 * El dominio nunca importa esta interfaz. Solo la application layer la usa.
 */
interface InventoryRepository {
    fun savePod(pod: Pod): SaveOutcome
    fun findPod(ref: ResourceRef): Pod?
    fun findPods(filter: PodFilter): List<Pod>

    fun saveService(service: Service): SaveOutcome
    fun findServices(filter: ServiceFilter): List<Service>

    fun saveIngress(ingress: Ingress): SaveOutcome
    fun findIngresses(filter: IngressFilter): List<Ingress>

    fun savePvc(pvc: PersistentVolumeClaim): SaveOutcome
    fun findPvcs(filter: PvcFilter): List<PersistentVolumeClaim>

    fun saveCertificate(certificate: Certificate): SaveOutcome
    fun findCertificates(filter: CertFilter): List<Certificate>
    fun findExpiringCertificates(thresholdDays: Long): List<Certificate>

    fun markDeleted(ref: ResourceRef): Boolean
}

sealed interface SaveOutcome {
    data object Inserted : SaveOutcome
    data object Updated : SaveOutcome

    /** El resourceVersion ya estaba persistido; sin cambios. */
    data object Unchanged : SaveOutcome
}
