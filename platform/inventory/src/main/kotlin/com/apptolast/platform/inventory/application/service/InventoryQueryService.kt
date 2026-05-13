package com.apptolast.platform.inventory.application.service

import com.apptolast.platform.inventory.application.port.inbound.CertFilter
import com.apptolast.platform.inventory.application.port.inbound.IngressFilter
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
import org.springframework.stereotype.Service as SpringService
import org.springframework.transaction.annotation.Transactional

@SpringService
@Transactional(readOnly = true)
class InventoryQueryService(
    private val repository: InventoryRepository,
) : QueryInventoryUseCase {

    override fun listPods(filter: PodFilter): List<Pod> = repository.findPods(filter)

    override fun getPod(ref: ResourceRef): Pod? = repository.findPod(ref)

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
