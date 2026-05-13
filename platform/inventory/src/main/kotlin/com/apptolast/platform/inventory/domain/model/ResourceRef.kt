package com.apptolast.platform.inventory.domain.model

/**
 * Identifica unívocamente un recurso k8s en el cluster.
 *
 * Reason: kind + namespace + name es la clave primaria efectiva. Una misma
 * combinación `(kind, namespace, name)` puede tener varios resourceVersion a
 * lo largo del tiempo, pero el `ResourceRef` permanece estable.
 */
data class ResourceRef(
    val kind: ResourceKind,
    val namespace: String,
    val name: String,
) {
    init {
        require(namespace.isNotBlank()) { "namespace must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
    }

    fun qualifiedName(): String = "$kind/$namespace/$name"
}

enum class ResourceKind {
    POD,
    SERVICE,
    INGRESS,
    PVC,
    CERTIFICATE,
    VOLUME,
    DNS_RECORD,
    ;

    companion object {
        fun fromString(value: String): ResourceKind = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true) ||
                value.equals(it.name.replace("_", ""), ignoreCase = true)
        } ?: error("Unknown ResourceKind: $value")
    }
}
