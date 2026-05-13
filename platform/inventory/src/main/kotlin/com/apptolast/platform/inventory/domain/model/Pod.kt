package com.apptolast.platform.inventory.domain.model

import java.time.Instant

/**
 * Modelo de dominio puro para Pod.
 *
 * Reason: no usamos directamente `io.fabric8.kubernetes.api.model.Pod` porque
 * eso ataría el dominio al SDK de Kubernetes (regla hexagonal). La traducción
 * de fabric8 → domain ocurre en `infrastructure/k8s/PodMapper.kt`.
 */
data class Pod(
    val ref: ResourceRef,
    val resourceVersion: String,
    val observedGeneration: Long,
    val phase: PodPhase,
    val nodeName: String?,
    val podIp: String?,
    val containers: List<Container>,
    val owner: OwnerReference?,
    val labels: Map<String, String>,
    val annotations: Map<String, String>,
    val observedAt: Instant,
) {
    init {
        require(ref.kind == ResourceKind.POD) { "ResourceRef must have kind=POD" }
    }

    fun isReady(): Boolean = phase == PodPhase.RUNNING && containers.all { it.ready }

    fun totalRestarts(): Int = containers.sumOf { it.restartCount }
}

enum class PodPhase { PENDING, RUNNING, SUCCEEDED, FAILED, UNKNOWN }

data class Container(
    val name: String,
    val image: String,
    val ready: Boolean,
    val restartCount: Int,
    val state: ContainerState,
) {
    init {
        require(name.isNotBlank()) { "container name must not be blank" }
        require(image.isNotBlank()) { "container image must not be blank" }
        require(restartCount >= 0) { "restartCount must be >= 0" }
    }
}

enum class ContainerState { WAITING, RUNNING, TERMINATED }

data class OwnerReference(
    val kind: String,
    val name: String,
)
