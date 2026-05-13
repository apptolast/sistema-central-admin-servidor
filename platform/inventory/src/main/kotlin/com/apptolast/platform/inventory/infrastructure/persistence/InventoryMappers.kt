package com.apptolast.platform.inventory.infrastructure.persistence

import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.IngressFlavor
import com.apptolast.platform.inventory.domain.model.OwnerReference
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.PvcPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.domain.model.Service
import com.apptolast.platform.inventory.domain.model.ServicePort
import com.apptolast.platform.inventory.domain.model.ServiceType
import com.apptolast.platform.inventory.infrastructure.persistence.entity.CertificateEntity
import com.apptolast.platform.inventory.infrastructure.persistence.entity.IngressEntity
import com.apptolast.platform.inventory.infrastructure.persistence.entity.PodEntity
import com.apptolast.platform.inventory.infrastructure.persistence.entity.PvcEntity
import com.apptolast.platform.inventory.infrastructure.persistence.entity.ServiceEntity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Mappers domain ↔ JPA entity.
 *
 * Justificación de mapeo: campos `containers`, `labels`, `annotations`, `ports`,
 * `hosts`, `dnsNames`, `accessModes`, `selector` se serializan como JSONB para
 * evitar tablas hijas con joins (lectura típica del controlador = 1 query).
 *
 * El [ObjectMapper] se inyecta vía Spring; Spring Boot ya configura uno con
 * `KotlinModule` + `JavaTimeModule` registrados por defecto.
 */
class InventoryJpaMapper(private val objectMapper: ObjectMapper) {

    fun toDomain(entity: PodEntity): Pod = Pod(
        ref = ResourceRef(ResourceKind.POD, entity.namespace, entity.name),
        resourceVersion = entity.resourceVersion,
        observedGeneration = entity.observedGeneration,
        phase = PodPhase.valueOf(entity.phase),
        nodeName = entity.nodeName,
        podIp = entity.podIp,
        containers = containersFromJson(entity.containers),
        owner = entity.ownerKind?.let { OwnerReference(it, entity.ownerName ?: "") },
        labels = objectMapper.readValue(entity.labels),
        annotations = objectMapper.readValue(entity.annotations),
        observedAt = entity.observedAt,
    )

    fun applyToEntity(domain: Pod, entity: PodEntity) {
        entity.namespace = domain.ref.namespace
        entity.name = domain.ref.name
        entity.resourceVersion = domain.resourceVersion
        entity.observedGeneration = domain.observedGeneration
        entity.phase = domain.phase.name
        entity.nodeName = domain.nodeName
        entity.podIp = domain.podIp
        entity.containers = objectMapper.writeValueAsString(
            domain.containers.map {
                mapOf(
                    "name" to it.name,
                    "image" to it.image,
                    "ready" to it.ready,
                    "restartCount" to it.restartCount,
                    "state" to it.state.name,
                )
            },
        )
        entity.ownerKind = domain.owner?.kind
        entity.ownerName = domain.owner?.name
        entity.labels = objectMapper.writeValueAsString(domain.labels)
        entity.annotations = objectMapper.writeValueAsString(domain.annotations)
        entity.observedAt = domain.observedAt
        entity.deletedAt = null
    }

    fun newEntity(domain: Pod): PodEntity = PodEntity(
        namespace = domain.ref.namespace,
        name = domain.ref.name,
        resourceVersion = domain.resourceVersion,
        observedGeneration = domain.observedGeneration,
        phase = domain.phase.name,
        nodeName = domain.nodeName,
        podIp = domain.podIp,
        containers = objectMapper.writeValueAsString(
            domain.containers.map {
                mapOf(
                    "name" to it.name,
                    "image" to it.image,
                    "ready" to it.ready,
                    "restartCount" to it.restartCount,
                    "state" to it.state.name,
                )
            },
        ),
        ownerKind = domain.owner?.kind,
        ownerName = domain.owner?.name,
        labels = objectMapper.writeValueAsString(domain.labels),
        annotations = objectMapper.writeValueAsString(domain.annotations),
        observedAt = domain.observedAt,
    )

    private data class ContainerJson(
        val name: String,
        val image: String,
        val ready: Boolean,
        val restartCount: Int,
        val state: String,
    )

    fun containersFromJson(json: String): List<Container> {
        val raw: List<ContainerJson> = objectMapper.readValue(json)
        return raw.map {
            Container(
                name = it.name,
                image = it.image,
                ready = it.ready,
                restartCount = it.restartCount,
                state = ContainerState.valueOf(it.state),
            )
        }
    }

    // ── Service ──────────────────────────────────────────────────────────────

    fun toDomain(entity: ServiceEntity): Service = Service(
        ref = ResourceRef(ResourceKind.SERVICE, entity.namespace, entity.name),
        resourceVersion = entity.resourceVersion,
        observedGeneration = entity.observedGeneration,
        type = ServiceType.valueOf(entity.type),
        clusterIp = entity.clusterIp,
        externalIp = entity.externalIp,
        ports = portsFromJson(entity.ports),
        selector = objectMapper.readValue(entity.selector),
        observedAt = entity.observedAt,
    )

    fun applyToEntity(domain: Service, entity: ServiceEntity) {
        entity.namespace = domain.ref.namespace
        entity.name = domain.ref.name
        entity.resourceVersion = domain.resourceVersion
        entity.observedGeneration = domain.observedGeneration
        entity.type = domain.type.name
        entity.clusterIp = domain.clusterIp
        entity.externalIp = domain.externalIp
        entity.ports = objectMapper.writeValueAsString(domain.ports.map { it.toJsonMap() })
        entity.selector = objectMapper.writeValueAsString(domain.selector)
        entity.observedAt = domain.observedAt
        entity.deletedAt = null
    }

    fun newEntity(domain: Service): ServiceEntity = ServiceEntity(
        namespace = domain.ref.namespace,
        name = domain.ref.name,
        resourceVersion = domain.resourceVersion,
        observedGeneration = domain.observedGeneration,
        type = domain.type.name,
        clusterIp = domain.clusterIp,
        externalIp = domain.externalIp,
        ports = objectMapper.writeValueAsString(domain.ports.map { it.toJsonMap() }),
        selector = objectMapper.writeValueAsString(domain.selector),
        observedAt = domain.observedAt,
    )

    private fun ServicePort.toJsonMap() = mapOf(
        "name" to name,
        "protocol" to protocol,
        "port" to port,
        "targetPort" to targetPort,
        "nodePort" to nodePort,
    )

    private data class PortJson(
        val name: String?,
        val protocol: String,
        val port: Int,
        val targetPort: String?,
        val nodePort: Int?,
    )

    private fun portsFromJson(json: String): List<ServicePort> {
        val raw: List<PortJson> = objectMapper.readValue(json)
        return raw.map {
            ServicePort(it.name, it.protocol, it.port, it.targetPort, it.nodePort)
        }
    }

    // ── Ingress ──────────────────────────────────────────────────────────────

    fun toDomain(entity: IngressEntity): Ingress = Ingress(
        ref = ResourceRef(ResourceKind.INGRESS, entity.namespace, entity.name),
        resourceVersion = entity.resourceVersion,
        observedGeneration = entity.observedGeneration,
        kind = IngressFlavor.valueOf(entity.kind),
        hosts = objectMapper.readValue(entity.hosts),
        tlsSecretName = entity.tlsSecretName,
        observedAt = entity.observedAt,
    )

    fun applyToEntity(domain: Ingress, entity: IngressEntity) {
        entity.namespace = domain.ref.namespace
        entity.name = domain.ref.name
        entity.kind = domain.kind.name
        entity.resourceVersion = domain.resourceVersion
        entity.observedGeneration = domain.observedGeneration
        entity.hosts = objectMapper.writeValueAsString(domain.hosts)
        entity.tlsSecretName = domain.tlsSecretName
        entity.observedAt = domain.observedAt
        entity.deletedAt = null
    }

    fun newEntity(domain: Ingress): IngressEntity = IngressEntity(
        namespace = domain.ref.namespace,
        name = domain.ref.name,
        kind = domain.kind.name,
        resourceVersion = domain.resourceVersion,
        observedGeneration = domain.observedGeneration,
        hosts = objectMapper.writeValueAsString(domain.hosts),
        tlsSecretName = domain.tlsSecretName,
        observedAt = domain.observedAt,
    )

    // ── PVC ──────────────────────────────────────────────────────────────────

    fun toDomain(entity: PvcEntity): PersistentVolumeClaim = PersistentVolumeClaim(
        ref = ResourceRef(ResourceKind.PVC, entity.namespace, entity.name),
        resourceVersion = entity.resourceVersion,
        observedGeneration = entity.observedGeneration,
        phase = PvcPhase.valueOf(entity.phase),
        storageClassName = entity.storageClassName,
        accessModes = objectMapper.readValue(entity.accessModes),
        requestedStorageBytes = entity.requestedStorageBytes,
        volumeName = entity.volumeName,
        observedAt = entity.observedAt,
    )

    fun applyToEntity(domain: PersistentVolumeClaim, entity: PvcEntity) {
        entity.namespace = domain.ref.namespace
        entity.name = domain.ref.name
        entity.resourceVersion = domain.resourceVersion
        entity.observedGeneration = domain.observedGeneration
        entity.phase = domain.phase.name
        entity.storageClassName = domain.storageClassName
        entity.accessModes = objectMapper.writeValueAsString(domain.accessModes)
        entity.requestedStorageBytes = domain.requestedStorageBytes
        entity.volumeName = domain.volumeName
        entity.observedAt = domain.observedAt
        entity.deletedAt = null
    }

    fun newEntity(domain: PersistentVolumeClaim): PvcEntity = PvcEntity(
        namespace = domain.ref.namespace,
        name = domain.ref.name,
        resourceVersion = domain.resourceVersion,
        observedGeneration = domain.observedGeneration,
        phase = domain.phase.name,
        storageClassName = domain.storageClassName,
        accessModes = objectMapper.writeValueAsString(domain.accessModes),
        requestedStorageBytes = domain.requestedStorageBytes,
        volumeName = domain.volumeName,
        observedAt = domain.observedAt,
    )

    // ── Certificate ──────────────────────────────────────────────────────────

    fun toDomain(entity: CertificateEntity): Certificate = Certificate(
        ref = ResourceRef(ResourceKind.CERTIFICATE, entity.namespace, entity.name),
        resourceVersion = entity.resourceVersion,
        observedGeneration = entity.observedGeneration,
        secretName = entity.secretName,
        dnsNames = objectMapper.readValue(entity.dnsNames),
        issuer = entity.issuer,
        ready = entity.ready,
        expiresAt = entity.expiresAt,
        observedAt = entity.observedAt,
    )

    fun applyToEntity(domain: Certificate, entity: CertificateEntity) {
        entity.namespace = domain.ref.namespace
        entity.name = domain.ref.name
        entity.resourceVersion = domain.resourceVersion
        entity.observedGeneration = domain.observedGeneration
        entity.secretName = domain.secretName
        entity.dnsNames = objectMapper.writeValueAsString(domain.dnsNames)
        entity.issuer = domain.issuer
        entity.ready = domain.ready
        entity.expiresAt = domain.expiresAt
        entity.observedAt = domain.observedAt
        entity.deletedAt = null
    }

    fun newEntity(domain: Certificate): CertificateEntity = CertificateEntity(
        namespace = domain.ref.namespace,
        name = domain.ref.name,
        resourceVersion = domain.resourceVersion,
        observedGeneration = domain.observedGeneration,
        secretName = domain.secretName,
        dnsNames = objectMapper.writeValueAsString(domain.dnsNames),
        issuer = domain.issuer,
        ready = domain.ready,
        expiresAt = domain.expiresAt,
        observedAt = domain.observedAt,
    )
}
