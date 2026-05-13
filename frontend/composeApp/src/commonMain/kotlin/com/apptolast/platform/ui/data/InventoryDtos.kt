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

/**
 * Detalle de Pod enriquecido con runbooks relevantes (vía knowledge module).
 *
 * Backend: `GET /api/v1/inventory/pods/{ns}/{name}` ahora devuelve este wrapper
 * (cambio aditivo en commit 85ca3c7). El campo `pod` mantiene los mismos campos
 * que [PodDto] para que cualquier cliente legado siga funcionando.
 *
 * Regla anti-hallucination: `relatedRunbooks` puede estar vacío si knowledge no
 * encontró evidencia o estaba caído. La UI NO debe mostrar "no hay runbooks";
 * simplemente oculta la sección.
 */
@Serializable
data class PodDetailDto(
    val pod: PodDto,
    val relatedRunbooks: List<RunbookCitationDto> = emptyList(),
)

@Serializable
data class RunbookCitationDto(
    val sourcePath: String,
    val section: String,
    val sha: String,
    /** Cita formato canónico `[source: path#section@sha]`. */
    val citation: String,
)

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
