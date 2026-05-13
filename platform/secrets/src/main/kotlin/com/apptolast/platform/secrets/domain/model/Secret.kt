package com.apptolast.platform.secrets.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Metadata observable de un secret en Passbolt.
 *
 * El plaintext NUNCA cruza este modelo — vive en Passbolt cifrado con GPG.
 * Aquí mantenemos sólo identidad, ownership, tags, rotation policy y audit log.
 */
data class Secret(
    val id: SecretId,
    val name: String,
    val owner: OwnerRef,
    val sharedWith: List<OwnerRef>,
    val tags: Set<String>,
    val rotationPolicy: RotationPolicy?,
    val lastRotatedAt: Instant?,
    val createdAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
    }

    fun daysSinceRotation(now: Instant = Instant.now()): Long? =
        lastRotatedAt?.let { ChronoUnit.DAYS.between(it, now) }

    fun isOverdue(now: Instant = Instant.now()): Boolean {
        val policy = rotationPolicy ?: return false
        val since = daysSinceRotation(now) ?: return true
        return since > policy.maxAgeDays
    }
}

@JvmInline
value class SecretId(val value: String) {
    init {
        require(value.isNotBlank()) { "SecretId must not be blank" }
    }
}

data class OwnerRef(
    val userSub: String,     // OIDC subject (Keycloak)
    val email: String,
    val displayName: String,
)

data class RotationPolicy(
    val maxAgeDays: Long,
    val notifyDaysBefore: Long = 7,
) {
    init {
        require(maxAgeDays > 0)
        require(notifyDaysBefore in 0..maxAgeDays)
    }
}
