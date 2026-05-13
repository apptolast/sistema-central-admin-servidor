package com.apptolast.platform.inventory.infrastructure.web

import com.apptolast.platform.inventory.application.port.inbound.CertFilter
import com.apptolast.platform.inventory.application.port.inbound.IngressFilter
import com.apptolast.platform.inventory.application.port.inbound.PodFilter
import com.apptolast.platform.inventory.application.port.inbound.PvcFilter
import com.apptolast.platform.inventory.application.port.inbound.QueryInventoryUseCase
import com.apptolast.platform.inventory.application.port.inbound.ServiceFilter
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.infrastructure.web.dto.CertificateDto
import com.apptolast.platform.inventory.infrastructure.web.dto.IngressDto
import com.apptolast.platform.inventory.infrastructure.web.dto.PodDetailDto
import com.apptolast.platform.inventory.infrastructure.web.dto.PodDto
import com.apptolast.platform.inventory.infrastructure.web.dto.PvcDto
import com.apptolast.platform.inventory.infrastructure.web.dto.ServiceDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST API del módulo Inventory (Phase 1 — solo lectura).
 *
 * Las escrituras llegan vía bus de eventos desde cluster-watcher (no expuestas en HTTP).
 *
 * Convención de paths: `/api/v1/inventory/{recurso-en-plural}`.
 */
@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController(
    private val query: QueryInventoryUseCase,
) {

    @GetMapping("/pods")
    fun listPods(
        @RequestParam(required = false) namespace: String?,
        @RequestParam(required = false) phase: String?,
    ): List<PodDto> = query.listPods(PodFilter(namespace = namespace, phase = phase))
        .map(PodDto::from)

    /**
     * Detalle de un pod, enriquecido con runbooks relevantes del knowledge module.
     * Si knowledge está caído, devuelve igualmente el pod con `relatedRunbooks=[]`.
     */
    @GetMapping("/pods/{namespace}/{name}")
    fun getPod(
        @PathVariable namespace: String,
        @PathVariable name: String,
    ): ResponseEntity<PodDetailDto> {
        val detail = query.getPodDetail(ResourceRef(ResourceKind.POD, namespace, name))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(PodDetailDto.from(detail))
    }

    @GetMapping("/services")
    fun listServices(
        @RequestParam(required = false) namespace: String?,
        @RequestParam(required = false) type: String?,
    ): List<ServiceDto> = query.listServices(ServiceFilter(namespace, type)).map(ServiceDto::from)

    @GetMapping("/ingresses")
    fun listIngresses(
        @RequestParam(required = false) namespace: String?,
        @RequestParam(required = false) host: String?,
    ): List<IngressDto> = query.listIngresses(IngressFilter(namespace, host)).map(IngressDto::from)

    @GetMapping("/pvcs")
    fun listPvcs(
        @RequestParam(required = false) namespace: String?,
        @RequestParam(required = false) phase: String?,
    ): List<PvcDto> = query.listPvcs(PvcFilter(namespace, phase)).map(PvcDto::from)

    @GetMapping("/certificates")
    fun listCertificates(
        @RequestParam(required = false) namespace: String?,
        @RequestParam(required = false) ready: Boolean?,
    ): List<CertificateDto> = query.listCertificates(CertFilter(namespace, ready)).map(CertificateDto::from)

    @GetMapping("/certificates/expiring")
    fun listExpiringCertificates(
        @RequestParam(defaultValue = "30") thresholdDays: Long,
    ): List<CertificateDto> =
        query.listExpiringCertificates(thresholdDays).map(CertificateDto::from)
}
