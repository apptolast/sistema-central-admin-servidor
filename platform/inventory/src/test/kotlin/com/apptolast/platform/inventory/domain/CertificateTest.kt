package com.apptolast.platform.inventory.domain

import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class CertificateTest {

    @Test
    fun `daysUntilExpiry returns null when expiresAt is null`() {
        val cert = aCert(expiresAt = null)
        cert.daysUntilExpiry() shouldBe null
    }

    @Test
    fun `isExpiringSoon true when expiry within threshold`() {
        val now = Instant.parse("2026-05-13T00:00:00Z")
        val cert = aCert(expiresAt = now.plus(7, ChronoUnit.DAYS))
        cert.isExpiringSoon(thresholdDays = 14, now = now) shouldBe true
    }

    @Test
    fun `isExpiringSoon false when expiry beyond threshold`() {
        val now = Instant.parse("2026-05-13T00:00:00Z")
        val cert = aCert(expiresAt = now.plus(60, ChronoUnit.DAYS))
        cert.isExpiringSoon(thresholdDays = 14, now = now) shouldBe false
    }

    @Test
    fun `isExpiringSoon false when expiresAt is null`() {
        val cert = aCert(expiresAt = null)
        cert.isExpiringSoon() shouldBe false
    }

    private fun aCert(expiresAt: Instant?) = Certificate(
        ref = ResourceRef(ResourceKind.CERTIFICATE, "monitoring", "grafana-tls"),
        resourceVersion = "1",
        observedGeneration = 1,
        secretName = "grafana-tls",
        dnsNames = listOf("grafana.apptolast.com"),
        issuer = "cloudflare-clusterissuer",
        ready = true,
        expiresAt = expiresAt,
        observedAt = Instant.parse("2026-05-13T00:00:00Z"),
    )
}
