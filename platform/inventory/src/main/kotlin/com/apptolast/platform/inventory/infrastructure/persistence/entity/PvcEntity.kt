package com.apptolast.platform.inventory.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "inventory_pvc",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_pvc_ns_name", columnNames = ["namespace", "name"]),
    ],
    indexes = [
        Index(name = "ix_pvc_ns", columnList = "namespace"),
        Index(name = "ix_pvc_phase", columnList = "phase"),
    ],
)
class PvcEntity(
    @Id
    @GeneratedValue
    var id: UUID? = null,

    @Column(nullable = false, length = 128)
    var namespace: String,

    @Column(nullable = false, length = 253)
    var name: String,

    @Column(name = "resource_version", nullable = false, length = 64)
    var resourceVersion: String,

    @Column(name = "observed_generation", nullable = false)
    var observedGeneration: Long,

    @Column(nullable = false, length = 16)
    var phase: String,

    @Column(name = "storage_class_name", length = 253)
    var storageClassName: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var accessModes: String,

    @Column(name = "requested_storage_bytes", nullable = false)
    var requestedStorageBytes: Long,

    @Column(name = "volume_name", length = 253)
    var volumeName: String? = null,

    @Column(name = "observed_at", nullable = false)
    var observedAt: Instant,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
