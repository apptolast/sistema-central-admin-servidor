package com.apptolast.platform.secrets.api.events

import java.time.Instant
import java.util.UUID

/**
 * Eventos públicos del módulo secrets.
 *
 * NOTA crítica: NUNCA incluyen el plaintext del secret. Sólo metadata.
 */
sealed interface SecretsEvent {
    val eventId: UUID
    val observedAt: Instant
    val secretId: String
}

data class SecretAccessed(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val secretId: String,
    val userSub: String,
    val sourceIp: String?,
    val accessType: AccessType,
    val reasonGiven: String?,
) : SecretsEvent {
    enum class AccessType { READ, EXPORT, SHARE, ROTATE_PROPOSAL }
}

data class SecretRotated(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val secretId: String,
    val rotatedBy: String,
    val previousVersionRetired: Boolean,
) : SecretsEvent

data class SecretMarkedExpiring(
    override val eventId: UUID = UUID.randomUUID(),
    override val observedAt: Instant = Instant.now(),
    override val secretId: String,
    val daysUntilExpiry: Long,
    val ownerUserSub: String,
) : SecretsEvent
