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
    name = "inventory_service",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_service_ns_name", columnNames = ["namespace", "name"]),
    ],
    indexes = [
        Index(name = "ix_service_ns", columnList = "namespace"),
        Index(name = "ix_service_type", columnList = "type"),
    ],
)
class ServiceEntity(
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

    @Column(nullable = false, length = 24)
    var type: String,

    @Column(name = "cluster_ip", length = 45)
    var clusterIp: String? = null,

    @Column(name = "external_ip", length = 45)
    var externalIp: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var ports: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var selector: String,

    @Column(name = "observed_at", nullable = false)
    var observedAt: Instant,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
