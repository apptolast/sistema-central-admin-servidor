package com.apptolast.platform.inventory.application.service

import com.apptolast.platform.inventory.application.port.inbound.CertFilter
import com.apptolast.platform.inventory.application.port.inbound.IngressFilter
import com.apptolast.platform.inventory.application.port.inbound.PodDetail
import com.apptolast.platform.inventory.application.port.inbound.PodFilter
import com.apptolast.platform.inventory.application.port.inbound.PvcFilter
import com.apptolast.platform.inventory.application.port.inbound.QueryInventoryUseCase
import com.apptolast.platform.inventory.application.port.inbound.ServiceFilter
import com.apptolast.platform.inventory.application.port.outbound.InventoryRepository
import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.domain.model.Service
import com.apptolast.platform.knowledge.application.port.inbound.QueryKnowledgePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service as SpringService
import org.springframework.transaction.annotation.Transactional

@SpringService
@Transactional(readOnly = true)
class InventoryQueryService(
    private val repository: InventoryRepository,
    /**
     * Opcional. Si no hay bean wireado (entorno dev sin rag-query disponible),
     * `getPodDetail` devuelve PodDetail con `relatedRunbooks = emptyList()`.
     */
    private val knowledge: QueryKnowledgePort? = null,
) : QueryInventoryUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun listPods(filter: PodFilter): List<Pod> = repository.findPods(filter)

    override fun getPod(ref: ResourceRef): Pod? = repository.findPod(ref)

    override fun getPodDetail(ref: ResourceRef): PodDetail? {
        val pod = repository.findPod(ref) ?: return null
        return PodDetail(pod = pod, relatedRunbooks = findRunbooksFor(pod))
    }

    private fun findRunbooksFor(pod: Pod): List<com.apptolast.platform.knowledge.domain.model.Citation> {
        val port = knowledge ?: return emptyList()
        return try {
            val q = "pod ${pod.ref.name} namespace ${pod.ref.namespace} runbook error"
            port.query(q, topK = 3)
        } catch (ex: Exception) {
            // Defensa adicional: RestKnowledgeClient ya traga RestClientException,
            // pero protegemos contra cualquier otro fallo inesperado (anti-hallucination:
            // mejor sin runbooks que pod-detail roto).
            log.warn("knowledge port threw unexpectedly, returning empty runbooks: {}", ex.message)
            emptyList()
        }
    }

    override fun listServices(filter: ServiceFilter): List<Service> =
        repository.findServices(filter)

    override fun listIngresses(filter: IngressFilter): List<Ingress> =
        repository.findIngresses(filter)

    override fun listPvcs(filter: PvcFilter): List<PersistentVolumeClaim> =
        repository.findPvcs(filter)

    override fun listCertificates(filter: CertFilter): List<Certificate> =
        repository.findCertificates(filter)

    override fun listExpiringCertificates(thresholdDays: Long): List<Certificate> =
        repository.findExpiringCertificates(thresholdDays)
}
