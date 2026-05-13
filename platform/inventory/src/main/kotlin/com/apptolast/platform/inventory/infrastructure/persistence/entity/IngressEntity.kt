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
    name = "inventory_ingress",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_ingress_ns_name_kind", columnNames = ["namespace", "name", "kind"]),
    ],
    indexes = [
        Index(name = "ix_ingress_ns", columnList = "namespace"),
    ],
)
class IngressEntity(
    @Id
    @GeneratedValue
    var id: UUID? = null,

    @Column(nullable = false, length = 128)
    var namespace: String,

    @Column(nullable = false, length = 253)
    var name: String,

    @Column(nullable = false, length = 48)
    var kind: String,

    @Column(name = "resource_version", nullable = false, length = 64)
    var resourceVersion: String,

    @Column(name = "observed_generation", nullable = false)
    var observedGeneration: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var hosts: String,

    @Column(name = "tls_secret_name", length = 253)
    var tlsSecretName: String? = null,

    @Column(name = "observed_at", nullable = false)
    var observedAt: Instant,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
