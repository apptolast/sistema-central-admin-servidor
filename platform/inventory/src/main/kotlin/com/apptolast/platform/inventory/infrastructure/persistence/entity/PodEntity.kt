package com.apptolast.platform.inventory.infrastructure.persistence.entity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
    name = "inventory_pod",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_pod_ns_name", columnNames = ["namespace", "name"]),
    ],
    indexes = [
        Index(name = "ix_pod_ns", columnList = "namespace"),
        Index(name = "ix_pod_phase", columnList = "phase"),
        Index(name = "ix_pod_deleted", columnList = "deleted_at"),
    ],
)
@JsonIgnoreProperties("hibernateLazyInitializer", "handler")
class PodEntity(
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

    @Column(name = "node_name", length = 253)
    var nodeName: String? = null,

    @Column(name = "pod_ip", length = 45)
    var podIp: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var containers: String,

    @Column(name = "owner_kind", length = 64)
    var ownerKind: String? = null,

    @Column(name = "owner_name", length = 253)
    var ownerName: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var labels: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var annotations: String,

    @Column(name = "observed_at", nullable = false)
    var observedAt: Instant,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
