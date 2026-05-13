package com.apptolast.platform.inventory.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit

data class Certificate(
    val ref: ResourceRef,
    val resourceVersion: String,
    val observedGeneration: Long,
    val secretName: String,
    val dnsNames: List<String>,
    val issuer: String,
    val ready: Boolean,
    val expiresAt: Instant?,
    val observedAt: Instant,
) {
    init {
        require(ref.kind == ResourceKind.CERTIFICATE) { "ResourceRef must have kind=CERTIFICATE" }
        require(dnsNames.isNotEmpty()) { "certificate must have at least 1 DNS name" }
    }

    /** Días hasta expiración o null si el cert no tiene fecha. Útil para alertas P1. */
    fun daysUntilExpiry(now: Instant = Instant.now()): Long? =
        expiresAt?.let { ChronoUnit.DAYS.between(now, it) }

    fun isExpiringSoon(thresholdDays: Long = 14L, now: Instant = Instant.now()): Boolean =
        daysUntilExpiry(now)?.let { it <= thresholdDays } ?: false
}
