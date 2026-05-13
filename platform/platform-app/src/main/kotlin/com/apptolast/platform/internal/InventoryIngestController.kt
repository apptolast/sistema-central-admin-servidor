package com.apptolast.platform.internal

import com.apptolast.platform.inventory.application.port.inbound.IngestResourceUseCase
import com.apptolast.platform.inventory.application.port.inbound.IngestResult
import com.apptolast.platform.inventory.domain.model.Certificate
import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Ingress
import com.apptolast.platform.inventory.domain.model.IngressFlavor
import com.apptolast.platform.inventory.domain.model.OwnerReference
import com.apptolast.platform.inventory.domain.model.PersistentVolumeClaim
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.PvcPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.domain.model.Service
import com.apptolast.platform.inventory.domain.model.ServicePort
import com.apptolast.platform.inventory.domain.model.ServiceType
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Endpoint INTERNO — cierra el loop con `cluster-watcher`.
 *
 * Phase 1 wire (ver ADR-0005): cluster-watcher hace POST aquí, el controller
 * traduce el payload genérico (Map) a dominio y delega a `IngestResourceUseCase`.
 *
 * Path namespaced bajo `/api/v1/internal/...` para que la NetworkPolicy lo aísle
 * a tráfico del namespace `platform` (cluster-watcher) o `cluster-ops`.
 *
 * NO está expuesto vía Traefik IngressRoute — solo accesible cluster-internal.
 */
@RestController
@RequestMapping("/api/v1/internal/inventory")
class InventoryIngestController(
    private val ingest: IngestResourceUseCase,
) {

    private val log = LoggerFactory.getLogger(InventoryIngestController::class.java)

    @PostMapping("/ingest")
    fun ingestEvent(@RequestBody payload: Map<String, Any?>): ResponseEntity<IngestResponse> {
        val kind = payload["kind"]?.toString() ?: return badRequest("missing 'kind'")
        val operation = payload["operation"]?.toString() ?: "UPDATE"
        val namespace = payload["namespace"]?.toString() ?: return badRequest("missing 'namespace'")
        val name = payload["name"]?.toString() ?: return badRequest("missing 'name'")

        log.debug("ingest kind={} op={} ns={} name={}", kind, operation, namespace, name)

        return try {
            val result = when {
                operation == "DELETE" -> {
                    ingest.markDeleted(ResourceRef(ResourceKind.fromString(kind), namespace, name))
                }
                kind == "POD" -> ingest.ingest(parsePod(payload))
                kind == "SERVICE" -> ingest.ingest(parseService(payload))
                kind == "PVC" -> ingest.ingest(parsePvc(payload))
                kind == "INGRESS" -> ingest.ingest(parseIngress(payload))
                kind == "CERTIFICATE" -> ingest.ingest(parseCertificate(payload))
                else -> return badRequest("unsupported kind: $kind")
            }
            ResponseEntity.ok(IngestResponse(result::class.simpleName ?: "Unknown"))
        } catch (e: IllegalArgumentException) {
            log.warn("invalid payload kind={} ns={} name={} err={}", kind, namespace, name, e.message)
            ResponseEntity.badRequest().body(IngestResponse("Rejected", e.message))
        }
    }

    private fun badRequest(reason: String): ResponseEntity<IngestResponse> =
        ResponseEntity.badRequest().body(IngestResponse("Rejected", reason))

    // ── Parsers (payload Map → domain) ───────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parsePod(payload: Map<String, Any?>): Pod {
        val containersJson = payload["containers"] as? List<Map<String, Any?>> ?: emptyList()
        return Pod(
            ref = ResourceRef(ResourceKind.POD, payload.req("namespace"), payload.req("name")),
            resourceVersion = payload["resourceVersion"]?.toString() ?: "0",
            observedGeneration = (payload["observedGeneration"] as? Number)?.toLong() ?: 0L,
            phase = PodPhase.valueOf((payload["phase"]?.toString() ?: "UNKNOWN").uppercase()),
            nodeName = payload["nodeName"]?.toString(),
            podIp = payload["podIp"]?.toString(),
            containers = containersJson.map { c ->
                Container(
                    name = c.req("name"),
                    image = c.req("image"),
                    ready = (c["ready"] as? Boolean) ?: false,
                    restartCount = (c["restartCount"] as? Number)?.toInt() ?: 0,
                    state = ContainerState.valueOf((c["state"]?.toString() ?: "WAITING").uppercase()),
                )
            },
            owner = payload["ownerReferenceKind"]?.let { kind ->
                OwnerReference(kind.toString(), payload["ownerReferenceName"]?.toString() ?: "")
            },
            labels = payload["labels"] as? Map<String, String> ?: emptyMap(),
            annotations = payload["annotations"] as? Map<String, String> ?: emptyMap(),
            observedAt = parseInstant(payload["observedAt"]),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseService(payload: Map<String, Any?>): Service {
        val portsJson = payload["ports"] as? List<Map<String, Any?>> ?: emptyList()
        return Service(
            ref = ResourceRef(ResourceKind.SERVICE, payload.req("namespace"), payload.req("name")),
            resourceVersion = payload["resourceVersion"]?.toString() ?: "0",
            observedGeneration = (payload["observedGeneration"] as? Number)?.toLong() ?: 0L,
            type = ServiceType.valueOf(
                (payload["type"]?.toString() ?: "CLUSTER_IP")
                    .replace("ClusterIP", "CLUSTER_IP")
                    .replace("NodePort", "NODE_PORT")
                    .replace("LoadBalancer", "LOAD_BALANCER")
                    .replace("ExternalName", "EXTERNAL_NAME")
                    .uppercase(),
            ),
            clusterIp = payload["clusterIp"]?.toString()?.takeIf { it != "None" },
            externalIp = payload["externalIp"]?.toString(),
            ports = portsJson.map { p ->
                ServicePort(
                    name = p["name"]?.toString(),
                    protocol = p["protocol"]?.toString() ?: "TCP",
                    port = (p["port"] as? Number)?.toInt() ?: 0,
                    targetPort = p["targetPort"]?.toString(),
                    nodePort = (p["nodePort"] as? Number)?.toInt(),
                )
            }.filter { it.port > 0 },
            selector = payload["selector"] as? Map<String, String> ?: emptyMap(),
            observedAt = parseInstant(payload["observedAt"]),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePvc(payload: Map<String, Any?>): PersistentVolumeClaim {
        val requested = (payload["requestedStorageBytes"] as? Number)?.toLong() ?: 1L
        return PersistentVolumeClaim(
            ref = ResourceRef(ResourceKind.PVC, payload.req("namespace"), payload.req("name")),
            resourceVersion = payload["resourceVersion"]?.toString() ?: "0",
            observedGeneration = (payload["observedGeneration"] as? Number)?.toLong() ?: 0L,
            phase = PvcPhase.valueOf((payload["phase"]?.toString() ?: "UNKNOWN").uppercase()),
            storageClassName = payload["storageClassName"]?.toString(),
            accessModes = payload["accessModes"] as? List<String> ?: emptyList(),
            requestedStorageBytes = requested,
            volumeName = payload["volumeName"]?.toString(),
            observedAt = parseInstant(payload["observedAt"]),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseIngress(payload: Map<String, Any?>): Ingress {
        val hosts = payload["hosts"] as? List<String> ?: listOf("unknown.local")
        return Ingress(
            ref = ResourceRef(ResourceKind.INGRESS, payload.req("namespace"), payload.req("name")),
            resourceVersion = payload["resourceVersion"]?.toString() ?: "0",
            observedGeneration = (payload["observedGeneration"] as? Number)?.toLong() ?: 0L,
            kind = IngressFlavor.valueOf(
                (payload["flavor"]?.toString() ?: "K8S_INGRESS").uppercase(),
            ),
            hosts = hosts,
            tlsSecretName = payload["tlsSecretName"]?.toString(),
            observedAt = parseInstant(payload["observedAt"]),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCertificate(payload: Map<String, Any?>): Certificate =
        Certificate(
            ref = ResourceRef(ResourceKind.CERTIFICATE, payload.req("namespace"), payload.req("name")),
            resourceVersion = payload["resourceVersion"]?.toString() ?: "0",
            observedGeneration = (payload["observedGeneration"] as? Number)?.toLong() ?: 0L,
            secretName = payload["secretName"]?.toString() ?: payload.req("name"),
            dnsNames = payload["dnsNames"] as? List<String> ?: listOf(payload.req("name")),
            issuer = payload["issuer"]?.toString() ?: "unknown",
            ready = (payload["ready"] as? Boolean) ?: false,
            expiresAt = payload["expiresAt"]?.toString()?.let(Instant::parse),
            observedAt = parseInstant(payload["observedAt"]),
        )

    private fun parseInstant(raw: Any?): Instant = when (raw) {
        null -> Instant.now()
        is String -> runCatching { Instant.parse(raw) }.getOrDefault(Instant.now())
        else -> Instant.now()
    }

    private fun Map<String, Any?>.req(key: String): String =
        this[key]?.toString() ?: throw IllegalArgumentException("missing '$key'")
}

data class IngestResponse(
    val outcome: String,
    val reason: String? = null,
)
