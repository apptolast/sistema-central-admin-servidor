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
    name = "inventory_certificate",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_cert_ns_name", columnNames = ["namespace", "name"]),
    ],
    indexes = [
        Index(name = "ix_cert_ns", columnList = "namespace"),
        Index(name = "ix_cert_expires", columnList = "expires_at"),
        Index(name = "ix_cert_ready", columnList = "ready"),
    ],
)
class CertificateEntity(
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

    @Column(name = "secret_name", nullable = false, length = 253)
    var secretName: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var dnsNames: String,

    @Column(nullable = false, length = 253)
    var issuer: String,

    @Column(nullable = false)
    var ready: Boolean,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "observed_at", nullable = false)
    var observedAt: Instant,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
