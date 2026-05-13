package com.apptolast.platform.ui.data

import kotlinx.serialization.Serializable

/**
 * DTOs cliente — espejo de los DTOs del backend
 * (platform/inventory/.../web/dto/InventoryDto.kt).
 *
 * Mantener en sync hasta que generemos código desde OpenAPI (Phase 3).
 */

@Serializable
data class PodDto(
    val namespace: String,
    val name: String,
    val phase: String,
    val nodeName: String? = null,
    val podIp: String? = null,
    val containers: List<ContainerDto> = emptyList(),
    val ownerKind: String? = null,
    val ownerName: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val restarts: Int = 0,
    val ready: Boolean = false,
    val observedAt: String,
) {
    @Serializable
    data class ContainerDto(
        val name: String,
        val image: String,
        val ready: Boolean,
        val restartCount: Int,
        val state: String,
    )
}

@Serializable
data class ServiceDto(
    val namespace: String,
    val name: String,
    val type: String,
    val clusterIp: String? = null,
    val externalIp: String? = null,
    val ports: List<PortDto> = emptyList(),
    val publicallyExposed: Boolean = false,
) {
    @Serializable
    data class PortDto(
        val name: String? = null,
        val protocol: String,
        val port: Int,
        val targetPort: String? = null,
        val nodePort: Int? = null,
    )
}

@Serializable
data class CertificateDto(
    val namespace: String,
    val name: String,
    val secretName: String,
    val dnsNames: List<String>,
    val issuer: String,
    val ready: Boolean,
    val expiresAt: String? = null,
    val daysUntilExpiry: Long? = null,
    val expiringSoon: Boolean = false,
)
