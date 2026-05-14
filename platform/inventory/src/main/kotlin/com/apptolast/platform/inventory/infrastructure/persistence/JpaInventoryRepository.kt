package com.apptolast.platform.inventory.infrastructure.persistence

import com.apptolast.platform.inventory.application.port.inbound.CertFilter
import com.apptolast.platform.inventory.application.port.inbound.IngressFilter
import com.apptolast.platform.inventory.application.port.inbound.PodFilter
import com.apptolast.platform.inventory.application.port.inbound.PvcFilter
import com.apptolast.platform.inventory.application.port.inbound.ServiceFilter
import com.apptolast.platform.inventory.application.port.outbound.InventoryRepository
import com.apptolast.platform.inventory.application.port.outbound.SaveOutcome
import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.domain.model.Service
import com.apptolast.platform.inventory.infrastructure.persistence.repository.CertificateJpaRepository
import com.apptolast.platform.inventory.infrastructure.persistence.entity.PodEntity
import com.apptolast.platform.inventory.infrastructure.persistence.repository.IngressJpaRepository
import com.apptolast.platform.inventory.infrastructure.persistence.repository.PodJpaRepository
import com.apptolast.platform.inventory.infrastructure.persistence.repository.PvcJpaRepository
import com.apptolast.platform.inventory.infrastructure.persistence.repository.ServiceJpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Repository
class JpaInventoryRepository(
    private val podRepo: PodJpaRepository,
    private val serviceRepo: ServiceJpaRepository,
    private val ingressRepo: IngressJpaRepository,
    private val pvcRepo: PvcJpaRepository,
    private val certRepo: CertificateJpaRepository,
    private val mapper: InventoryJpaMapper,
) : InventoryRepository {

    override fun savePod(pod: Pod): SaveOutcome {
        val existing = podRepo.findByNamespaceAndNameAndDeletedAtIsNull(pod.ref.namespace, pod.ref.name)
        return when {
            existing == null -> {
                podRepo.save(mapper.newEntity(pod))
                SaveOutcome.Inserted
            }
            existing.resourceVersion == pod.resourceVersion -> {
                existing.observedAt = pod.observedAt
                podRepo.save(existing)
                SaveOutcome.Unchanged
            }
            else -> {
                mapper.applyToEntity(pod, existing)
                podRepo.save(existing)
                SaveOutcome.Updated
            }
        }
    }

    override fun findPod(ref: ResourceRef): Pod? {
        require(ref.kind == ResourceKind.POD)
        return podRepo.findByNamespaceAndNameAndDeletedAtIsNull(ref.namespace, ref.name)
            ?.takeIf(::isFreshPod)
            ?.let(mapper::toDomain)
    }

    override fun findPods(filter: PodFilter): List<Pod> {
        val entities = when {
            filter.namespace != null && filter.phase != null ->
                podRepo.findAllByNamespaceAndPhaseAndDeletedAtIsNull(filter.namespace, filter.phase)
            filter.namespace != null ->
                podRepo.findAllByNamespaceAndDeletedAtIsNull(filter.namespace)
            filter.phase != null ->
                podRepo.findAllByPhaseAndDeletedAtIsNull(filter.phase)
            else -> podRepo.findAllByDeletedAtIsNull()
        }
        val matchesLabels: (Pod) -> Boolean = { pod ->
            filter.labelSelector.all { (k, v) -> pod.labels[k] == v }
        }
        return entities.filter(::isFreshPod).map(mapper::toDomain).filter(matchesLabels)
    }

    override fun saveService(service: Service): SaveOutcome {
        val existing = serviceRepo.findByNamespaceAndNameAndDeletedAtIsNull(
            service.ref.namespace,
            service.ref.name,
        )
        return when {
            existing == null -> {
                serviceRepo.save(mapper.newEntity(service))
                SaveOutcome.Inserted
            }
            existing.resourceVersion == service.resourceVersion -> SaveOutcome.Unchanged
            else -> {
                mapper.applyToEntity(service, existing)
                serviceRepo.save(existing)
                SaveOutcome.Updated
            }
        }
    }

    override fun findServices(filter: ServiceFilter): List<Service> {
        val entities = when {
            filter.namespace != null && filter.type != null ->
                serviceRepo.findAllByNamespaceAndTypeAndDeletedAtIsNull(filter.namespace, filter.type)
            filter.namespace != null ->
                serviceRepo.findAllByNamespaceAndDeletedAtIsNull(filter.namespace)
            filter.type != null ->
                serviceRepo.findAllByTypeAndDeletedAtIsNull(filter.type)
            else -> serviceRepo.findAllByDeletedAtIsNull()
        }
        return entities.map(mapper::toDomain)
    }

    override fun saveIngress(ingress: Ingress): SaveOutcome {
        val existing = ingressRepo.findByNamespaceAndNameAndKindAndDeletedAtIsNull(
            ingress.ref.namespace,
            ingress.ref.name,
            ingress.kind.name,
        )
        return when {
            existing == null -> {
                ingressRepo.save(mapper.newEntity(ingress))
                SaveOutcome.Inserted
            }
            existing.resourceVersion == ingress.resourceVersion -> SaveOutcome.Unchanged
            else -> {
                mapper.applyToEntity(ingress, existing)
                ingressRepo.save(existing)
                SaveOutcome.Updated
            }
        }
    }

    override fun findIngresses(filter: IngressFilter): List<Ingress> {
        val entities = if (filter.namespace != null) {
            ingressRepo.findAllByNamespaceAndDeletedAtIsNull(filter.namespace)
        } else {
            ingressRepo.findAllByDeletedAtIsNull()
        }
        val hostMatch: (Ingress) -> Boolean = { ing ->
            filter.host?.let { wanted -> ing.hosts.any { it == wanted } } ?: true
        }
        return entities.map(mapper::toDomain).filter(hostMatch)
    }

    override fun savePvc(pvc: PersistentVolumeClaim): SaveOutcome {
        val existing = pvcRepo.findByNamespaceAndNameAndDeletedAtIsNull(pvc.ref.namespace, pvc.ref.name)
        return when {
            existing == null -> {
                pvcRepo.save(mapper.newEntity(pvc))
                SaveOutcome.Inserted
            }
            existing.resourceVersion == pvc.resourceVersion -> SaveOutcome.Unchanged
            else -> {
                mapper.applyToEntity(pvc, existing)
                pvcRepo.save(existing)
                SaveOutcome.Updated
            }
        }
    }

    override fun findPvcs(filter: PvcFilter): List<PersistentVolumeClaim> {
        val entities = when {
            filter.namespace != null && filter.phase != null ->
                pvcRepo.findAllByNamespaceAndPhaseAndDeletedAtIsNull(filter.namespace, filter.phase)
            filter.namespace != null ->
                pvcRepo.findAllByNamespaceAndDeletedAtIsNull(filter.namespace)
            filter.phase != null ->
                pvcRepo.findAllByPhaseAndDeletedAtIsNull(filter.phase)
            else -> pvcRepo.findAllByDeletedAtIsNull()
        }
        return entities.map(mapper::toDomain)
    }

    override fun saveCertificate(certificate: Certificate): SaveOutcome {
        val existing = certRepo.findByNamespaceAndNameAndDeletedAtIsNull(
            certificate.ref.namespace,
            certificate.ref.name,
        )
        return when {
            existing == null -> {
                certRepo.save(mapper.newEntity(certificate))
                SaveOutcome.Inserted
            }
            existing.resourceVersion == certificate.resourceVersion -> SaveOutcome.Unchanged
            else -> {
                mapper.applyToEntity(certificate, existing)
                certRepo.save(existing)
                SaveOutcome.Updated
            }
        }
    }

    override fun findCertificates(filter: CertFilter): List<Certificate> {
        val entities = when {
            filter.namespace != null ->
                certRepo.findAllByNamespaceAndDeletedAtIsNull(filter.namespace)
            filter.ready != null ->
                certRepo.findAllByReadyAndDeletedAtIsNull(filter.ready)
            else -> certRepo.findAllByDeletedAtIsNull()
        }
        return entities.map(mapper::toDomain)
    }

    override fun findExpiringCertificates(thresholdDays: Long): List<Certificate> {
        val threshold = Instant.now().plus(thresholdDays, ChronoUnit.DAYS)
        return certRepo.findExpiringBefore(threshold).map(mapper::toDomain)
    }

    override fun markDeleted(ref: ResourceRef): Boolean {
        val now = Instant.now()
        return when (ref.kind) {
            ResourceKind.POD -> podRepo.findByNamespaceAndNameAndDeletedAtIsNull(ref.namespace, ref.name)
                ?.also { it.deletedAt = now; podRepo.save(it) } != null
            ResourceKind.SERVICE -> serviceRepo.findByNamespaceAndNameAndDeletedAtIsNull(ref.namespace, ref.name)
                ?.also { it.deletedAt = now; serviceRepo.save(it) } != null
            ResourceKind.INGRESS -> {
                val all = ingressRepo.findAllByNamespaceAndDeletedAtIsNull(ref.namespace)
                    .filter { it.name == ref.name }
                if (all.isEmpty()) return false
                all.forEach { it.deletedAt = now; ingressRepo.save(it) }
                true
            }
            ResourceKind.PVC -> pvcRepo.findByNamespaceAndNameAndDeletedAtIsNull(ref.namespace, ref.name)
                ?.also { it.deletedAt = now; pvcRepo.save(it) } != null
            ResourceKind.CERTIFICATE -> certRepo.findByNamespaceAndNameAndDeletedAtIsNull(ref.namespace, ref.name)
                ?.also { it.deletedAt = now; certRepo.save(it) } != null
            ResourceKind.VOLUME, ResourceKind.DNS_RECORD ->
                error("kind ${ref.kind} not yet supported in Phase 1")
        }
    }

    private fun isFreshPod(pod: PodEntity): Boolean =
        pod.observedAt.isAfter(Instant.now().minus(POD_STALE_AFTER_MINUTES, ChronoUnit.MINUTES))
}

private const val POD_STALE_AFTER_MINUTES = 20L
