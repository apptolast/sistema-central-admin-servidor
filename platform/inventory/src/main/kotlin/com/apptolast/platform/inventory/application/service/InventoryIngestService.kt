package com.apptolast.platform.inventory.application.service

import com.apptolast.platform.inventory.api.events.CertObserved
import com.apptolast.platform.inventory.api.events.IngressObserved
import com.apptolast.platform.inventory.api.events.PodObserved
import com.apptolast.platform.inventory.api.events.PvcObserved
import com.apptolast.platform.inventory.api.events.ResourceDeleted
import com.apptolast.platform.inventory.api.events.ServiceObserved
import com.apptolast.platform.inventory.application.port.inbound.IngestResourceUseCase
import com.apptolast.platform.inventory.application.port.inbound.IngestResult
import com.apptolast.platform.inventory.application.port.outbound.InventoryEventPublisher
import com.apptolast.platform.inventory.application.port.outbound.InventoryRepository
import com.apptolast.platform.inventory.application.port.outbound.SaveOutcome
import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.domain.model.Service
import org.springframework.stereotype.Service as SpringService
import org.springframework.transaction.annotation.Transactional

@SpringService
@Transactional
class InventoryIngestService(
    private val repository: InventoryRepository,
    private val publisher: InventoryEventPublisher,
) : IngestResourceUseCase {

    override fun ingest(pod: Pod): IngestResult {
        return when (val outcome = repository.savePod(pod)) {
            SaveOutcome.Inserted -> {
                publisher.publish(pod.toEvent())
                IngestResult.Created
            }
            SaveOutcome.Updated -> {
                publisher.publish(pod.toEvent())
                IngestResult.Updated
            }
            SaveOutcome.Unchanged -> IngestResult.Unchanged
        }
    }

    override fun ingest(service: Service): IngestResult {
        return when (repository.saveService(service)) {
            SaveOutcome.Inserted -> { publisher.publish(service.toEvent()); IngestResult.Created }
            SaveOutcome.Updated -> { publisher.publish(service.toEvent()); IngestResult.Updated }
            SaveOutcome.Unchanged -> IngestResult.Unchanged
        }
    }

    override fun ingest(ingress: Ingress): IngestResult {
        return when (repository.saveIngress(ingress)) {
            SaveOutcome.Inserted -> { publisher.publish(ingress.toEvent()); IngestResult.Created }
            SaveOutcome.Updated -> { publisher.publish(ingress.toEvent()); IngestResult.Updated }
            SaveOutcome.Unchanged -> IngestResult.Unchanged
        }
    }

    override fun ingest(pvc: PersistentVolumeClaim): IngestResult {
        return when (repository.savePvc(pvc)) {
            SaveOutcome.Inserted -> { publisher.publish(pvc.toEvent()); IngestResult.Created }
            SaveOutcome.Updated -> { publisher.publish(pvc.toEvent()); IngestResult.Updated }
            SaveOutcome.Unchanged -> IngestResult.Unchanged
        }
    }

    override fun ingest(certificate: Certificate): IngestResult {
        return when (repository.saveCertificate(certificate)) {
            SaveOutcome.Inserted -> { publisher.publish(certificate.toEvent()); IngestResult.Created }
            SaveOutcome.Updated -> { publisher.publish(certificate.toEvent()); IngestResult.Updated }
            SaveOutcome.Unchanged -> IngestResult.Unchanged
        }
    }

    override fun markDeleted(ref: ResourceRef): IngestResult {
        val deleted = repository.markDeleted(ref)
        if (!deleted) return IngestResult.Rejected("resource not found: ${ref.qualifiedName()}")
        publisher.publish(
            ResourceDeleted(
                namespace = ref.namespace,
                name = ref.name,
                resourceVersion = "",
                observedGeneration = 0,
                kind = ref.kind.name,
            ),
        )
        return IngestResult.Deleted
    }

    private fun Pod.toEvent(): PodObserved = PodObserved(
        observedAt = observedAt,
        namespace = ref.namespace,
        name = ref.name,
        resourceVersion = resourceVersion,
        observedGeneration = observedGeneration,
        phase = phase.name,
        nodeName = nodeName,
        podIp = podIp,
        containers = containers.map {
            PodObserved.ContainerSnapshot(
                name = it.name,
                image = it.image,
                ready = it.ready,
                restartCount = it.restartCount,
                state = it.state.name,
            )
        },
        ownerReferenceKind = owner?.kind,
        ownerReferenceName = owner?.name,
        labels = labels,
        annotations = annotations,
    )

    private fun Service.toEvent(): ServiceObserved = ServiceObserved(
        observedAt = observedAt,
        namespace = ref.namespace,
        name = ref.name,
        resourceVersion = resourceVersion,
        observedGeneration = observedGeneration,
        type = type.name,
        clusterIp = clusterIp,
        externalIp = externalIp,
        ports = ports.map {
            ServiceObserved.PortSnapshot(
                name = it.name,
                protocol = it.protocol,
                port = it.port,
                targetPort = it.targetPort,
                nodePort = it.nodePort,
            )
        },
        selector = selector,
    )

    private fun Ingress.toEvent(): IngressObserved = IngressObserved(
        observedAt = observedAt,
        namespace = ref.namespace,
        name = ref.name,
        resourceVersion = resourceVersion,
        observedGeneration = observedGeneration,
        kind = IngressObserved.IngressKind.valueOf(kind.name),
        hosts = hosts,
        tlsSecretName = tlsSecretName,
    )

    private fun PersistentVolumeClaim.toEvent(): PvcObserved = PvcObserved(
        observedAt = observedAt,
        namespace = ref.namespace,
        name = ref.name,
        resourceVersion = resourceVersion,
        observedGeneration = observedGeneration,
        phase = phase.name,
        storageClassName = storageClassName,
        accessModes = accessModes,
        requestedStorageBytes = requestedStorageBytes,
        volumeName = volumeName,
    )

    private fun Certificate.toEvent(): CertObserved = CertObserved(
        observedAt = observedAt,
        namespace = ref.namespace,
        name = ref.name,
        resourceVersion = resourceVersion,
        observedGeneration = observedGeneration,
        secretName = secretName,
        dnsNames = dnsNames,
        issuer = issuer,
        ready = ready,
        expiresAt = expiresAt,
    )
}
