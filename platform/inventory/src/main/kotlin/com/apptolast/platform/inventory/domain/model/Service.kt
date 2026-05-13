package com.apptolast.platform.inventory.domain.model

import java.time.Instant

data class Service(
    val ref: ResourceRef,
    val resourceVersion: String,
    val observedGeneration: Long,
    val type: ServiceType,
    val clusterIp: String?,
    val externalIp: String?,
    val ports: List<ServicePort>,
    val selector: Map<String, String>,
    val observedAt: Instant,
) {
    init {
        require(ref.kind == ResourceKind.SERVICE) { "ResourceRef must have kind=SERVICE" }
    }

    fun isPublicallyExposed(): Boolean = type == ServiceType.LOAD_BALANCER ||
        type == ServiceType.NODE_PORT
}

enum class ServiceType { CLUSTER_IP, NODE_PORT, LOAD_BALANCER, EXTERNAL_NAME }

data class ServicePort(
    val name: String?,
    val protocol: String,
    val port: Int,
    val targetPort: String?,
    val nodePort: Int?,
) {
    init {
        require(port in 1..65535) { "port must be in 1..65535, got $port" }
        nodePort?.let { require(it in 30000..32767) { "nodePort must be 30000..32767, got $it" } }
    }
}
