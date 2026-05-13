package com.apptolast.platform.inventory.infrastructure.persistence.repository

import com.apptolast.platform.inventory.infrastructure.persistence.entity.CertificateEntity
import com.apptolast.platform.inventory.infrastructure.persistence.entity.IngressEntity
import com.apptolast.platform.inventory.infrastructure.persistence.entity.PodEntity
import com.apptolast.platform.inventory.infrastructure.persistence.entity.PvcEntity
import com.apptolast.platform.inventory.infrastructure.persistence.entity.ServiceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PodJpaRepository : JpaRepository<PodEntity, UUID> {
    fun findByNamespaceAndNameAndDeletedAtIsNull(namespace: String, name: String): PodEntity?
    fun findAllByDeletedAtIsNull(): List<PodEntity>
    fun findAllByNamespaceAndDeletedAtIsNull(namespace: String): List<PodEntity>
    fun findAllByNamespaceAndPhaseAndDeletedAtIsNull(namespace: String, phase: String): List<PodEntity>
    fun findAllByPhaseAndDeletedAtIsNull(phase: String): List<PodEntity>
}

interface ServiceJpaRepository : JpaRepository<ServiceEntity, UUID> {
    fun findByNamespaceAndNameAndDeletedAtIsNull(namespace: String, name: String): ServiceEntity?
    fun findAllByDeletedAtIsNull(): List<ServiceEntity>
    fun findAllByNamespaceAndDeletedAtIsNull(namespace: String): List<ServiceEntity>
    fun findAllByTypeAndDeletedAtIsNull(type: String): List<ServiceEntity>
    fun findAllByNamespaceAndTypeAndDeletedAtIsNull(namespace: String, type: String): List<ServiceEntity>
}

interface IngressJpaRepository : JpaRepository<IngressEntity, UUID> {
    fun findByNamespaceAndNameAndKindAndDeletedAtIsNull(
        namespace: String,
        name: String,
        kind: String,
    ): IngressEntity?

    fun findAllByDeletedAtIsNull(): List<IngressEntity>
    fun findAllByNamespaceAndDeletedAtIsNull(namespace: String): List<IngressEntity>
}

interface PvcJpaRepository : JpaRepository<PvcEntity, UUID> {
    fun findByNamespaceAndNameAndDeletedAtIsNull(namespace: String, name: String): PvcEntity?
    fun findAllByDeletedAtIsNull(): List<PvcEntity>
    fun findAllByNamespaceAndDeletedAtIsNull(namespace: String): List<PvcEntity>
    fun findAllByPhaseAndDeletedAtIsNull(phase: String): List<PvcEntity>
    fun findAllByNamespaceAndPhaseAndDeletedAtIsNull(namespace: String, phase: String): List<PvcEntity>
}

interface CertificateJpaRepository : JpaRepository<CertificateEntity, UUID> {
    fun findByNamespaceAndNameAndDeletedAtIsNull(namespace: String, name: String): CertificateEntity?
    fun findAllByDeletedAtIsNull(): List<CertificateEntity>
    fun findAllByNamespaceAndDeletedAtIsNull(namespace: String): List<CertificateEntity>
    fun findAllByReadyAndDeletedAtIsNull(ready: Boolean): List<CertificateEntity>

    @Query(
        """
        SELECT c FROM CertificateEntity c
        WHERE c.deletedAt IS NULL
          AND c.expiresAt IS NOT NULL
          AND c.expiresAt <= :threshold
        ORDER BY c.expiresAt ASC
        """,
    )
    fun findExpiringBefore(@Param("threshold") threshold: Instant): List<CertificateEntity>
}
