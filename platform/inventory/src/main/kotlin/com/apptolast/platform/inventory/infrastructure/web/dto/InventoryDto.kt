package com.apptolast.platform.inventory.infrastructure.web.dto

import com.apptolast.platform.inventory.application.port.inbound.PodDetail
import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.Service
import com.apptolast.platform.knowledge.domain.model.Citation
import java.time.Instant

/** DTO REST plano — desacoplado del modelo de dominio. */
data class PodDto(
    val namespace: String,
    val name: String,
    val phase: String,
    val nodeName: String?,
    val podIp: String?,
    val containers: List<ContainerDto>,
    val ownerKind: String?,
    val ownerName: String?,
    val labels: Map<String, String>,
    val restarts: Int,
    val ready: Boolean,
    val observedAt: Instant,
) {
    data class ContainerDto(
        val name: String,
        val image: String,
        val ready: Boolean,
        val restartCount: Int,
        val state: String,
    )

    companion object {
        fun from(pod: Pod): PodDto = PodDto(
            namespace = pod.ref.namespace,
            name = pod.ref.name,
            phase = pod.phase.name,
            nodeName = pod.nodeName,
            podIp = pod.podIp,
            containers = pod.containers.map {
                ContainerDto(it.name, it.image, it.ready, it.restartCount, it.state.name)
            },
            ownerKind = pod.owner?.kind,
            ownerName = pod.owner?.name,
            labels = pod.labels,
            restarts = pod.totalRestarts(),
            ready = pod.isReady(),
            observedAt = pod.observedAt,
        )
    }
}

/**
 * Versión enriquecida del pod: añade [relatedRunbooks] resueltos vía Knowledge.
 * Si Knowledge no encontró evidencia o estaba caído, la lista queda vacía
 * (regla anti-hallucination). El frontend renderiza la sección "Runbooks
 * relacionados" sólo si `relatedRunbooks.isNotEmpty()`.
 */
data class PodDetailDto(
    val pod: PodDto,
    val relatedRunbooks: List<RunbookRefDto>,
) {
    data class RunbookRefDto(
        val sourcePath: String,
        val section: String,
        val sha: String,
        /** Cita en formato canónico `[source: path#section@sha]`. */
        val citation: String,
    ) {
        companion object {
            fun from(c: Citation): RunbookRefDto = RunbookRefDto(
                sourcePath = c.sourcePath,
                section = c.section,
                sha = c.sha,
                citation = c.toMarkdown(),
            )
        }
    }

    companion object {
        fun from(detail: PodDetail): PodDetailDto = PodDetailDto(
            pod = PodDto.from(detail.pod),
            relatedRunbooks = detail.relatedRunbooks.map(RunbookRefDto::from),
        )
    }
}

data class ServiceDto(
    val namespace: String,
    val name: String,
    val type: String,
    val clusterIp: String?,
    val externalIp: String?,
    val ports: List<PortDto>,
    val publicallyExposed: Boolean,
) {
    data class PortDto(
        val name: String?,
        val protocol: String,
        val port: Int,
        val targetPort: String?,
        val nodePort: Int?,
    )

    companion object {
        fun from(s: Service): ServiceDto = ServiceDto(
            namespace = s.ref.namespace,
            name = s.ref.name,
            type = s.type.name,
            clusterIp = s.clusterIp,
            externalIp = s.externalIp,
            ports = s.ports.map { PortDto(it.name, it.protocol, it.port, it.targetPort, it.nodePort) },
            publicallyExposed = s.isPublicallyExposed(),
        )
    }
}

data class IngressDto(
    val namespace: String,
    val name: String,
    val kind: String,
    val hosts: List<String>,
    val tlsSecretName: String?,
) {
    companion object {
        fun from(i: Ingress): IngressDto = IngressDto(
            namespace = i.ref.namespace,
            name = i.ref.name,
            kind = i.kind.name,
            hosts = i.hosts,
            tlsSecretName = i.tlsSecretName,
        )
    }
}

data class PvcDto(
    val namespace: String,
    val name: String,
    val phase: String,
    val storageClassName: String?,
    val accessModes: List<String>,
    val requestedStorageBytes: Long,
    val volumeName: String?,
    val bound: Boolean,
) {
    companion object {
        fun from(p: PersistentVolumeClaim): PvcDto = PvcDto(
            namespace = p.ref.namespace,
            name = p.ref.name,
            phase = p.phase.name,
            storageClassName = p.storageClassName,
            accessModes = p.accessModes,
            requestedStorageBytes = p.requestedStorageBytes,
            volumeName = p.volumeName,
            bound = p.isBound(),
        )
    }
}

data class CertificateDto(
    val namespace: String,
    val name: String,
    val secretName: String,
    val dnsNames: List<String>,
    val issuer: String,
    val ready: Boolean,
    val expiresAt: Instant?,
    val daysUntilExpiry: Long?,
    val expiringSoon: Boolean,
) {
    companion object {
        fun from(c: Certificate): CertificateDto = CertificateDto(
            namespace = c.ref.namespace,
            name = c.ref.name,
            secretName = c.secretName,
            dnsNames = c.dnsNames,
            issuer = c.issuer,
            ready = c.ready,
            expiresAt = c.expiresAt,
            daysUntilExpiry = c.daysUntilExpiry(),
            expiringSoon = c.isExpiringSoon(),
        )
    }
}
