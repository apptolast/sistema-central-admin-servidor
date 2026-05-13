package com.apptolast.platform.inventory.application.port.inbound

import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.domain.model.Service

/**
 * Puerto inbound (driving): operaciones de escritura del inventario.
 *
 * Consumido por el adaptador que recibe eventos del cluster-watcher (bus
 * in-memory en Fase 1, NATS JetStream en Fase 2).
 *
 * Idempotencia: si el `resourceVersion` ya está observado para el mismo ref,
 * el ingest es no-op (descarta el evento duplicado).
 */
interface IngestResourceUseCase {
    fun ingest(pod: Pod): IngestResult
    fun ingest(service: Service): IngestResult
    fun ingest(ingress: Ingress): IngestResult
    fun ingest(pvc: PersistentVolumeClaim): IngestResult
    fun ingest(certificate: Certificate): IngestResult
    fun markDeleted(ref: ResourceRef): IngestResult
}

sealed interface IngestResult {
    data object Created : IngestResult
    data object Updated : IngestResult
    data object Unchanged : IngestResult
    data object Deleted : IngestResult
    data class Rejected(val reason: String) : IngestResult
}
