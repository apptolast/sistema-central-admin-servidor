package com.apptolast.platform.automation.infrastructure.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "automation_audit_log",
    indexes = [
        Index(name = "ix_audit_executed_at", columnList = "executed_at"),
        Index(name = "ix_audit_command_kind", columnList = "command_kind"),
        Index(name = "ix_audit_user_id", columnList = "user_id"),
        Index(name = "ix_audit_outcome", columnList = "outcome"),
    ],
)
@JsonIgnoreProperties("hibernateLazyInitializer", "handler")
class AuditLogEntity(
    @Id
    var id: UUID,

    @Column(name = "executed_at", nullable = false)
    var executedAt: Instant,

    @Column(name = "command_kind", nullable = false, length = 64)
    var commandKind: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "command_payload", columnDefinition = "jsonb", nullable = false)
    var commandPayload: String,

    @Column(name = "executor_kind", nullable = false, length = 32)
    var executorKind: String,

    @Column(nullable = false, length = 16)
    var outcome: String,

    @Column(name = "exit_code")
    var exitCode: Int? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @Column(name = "stdout_excerpt", columnDefinition = "TEXT")
    var stdoutExcerpt: String? = null,

    @Column(name = "stderr_excerpt", columnDefinition = "TEXT")
    var stderrExcerpt: String? = null,

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    var rejectionReason: String? = null,

    @Column(name = "user_id", length = 128)
    var userId: String? = null,

    @Column(name = "correlation_id")
    var correlationId: UUID? = null,
)
