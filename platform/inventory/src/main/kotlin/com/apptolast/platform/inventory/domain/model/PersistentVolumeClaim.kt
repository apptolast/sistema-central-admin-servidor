package com.apptolast.platform.inventory.domain.model

import java.time.Instant

data class PersistentVolumeClaim(
    val ref: ResourceRef,
    val resourceVersion: String,
    val observedGeneration: Long,
    val phase: PvcPhase,
    val storageClassName: String?,
    val accessModes: List<String>,
    val requestedStorageBytes: Long,
    val volumeName: String?,
    val observedAt: Instant,
) {
    init {
        require(ref.kind == ResourceKind.PVC) { "ResourceRef must have kind=PVC" }
        require(requestedStorageBytes > 0) { "requestedStorageBytes must be > 0" }
    }

    fun isBound(): Boolean = phase == PvcPhase.BOUND
}

enum class PvcPhase { PENDING, BOUND, LOST, RELEASED, UNKNOWN }
