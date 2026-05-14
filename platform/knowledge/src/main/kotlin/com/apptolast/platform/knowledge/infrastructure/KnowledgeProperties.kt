package com.apptolast.platform.knowledge.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Config del cliente knowledge.
 *
 * Defaults: rag-query Service interno creado por el chart `rag-query` en el
 * namespace `platform`. El timeout es agresivo a propósito (2s): el bus de la
 * query principal no puede quedarse bloqueado esperando al RAG.
 */
@ConfigurationProperties(prefix = "rag.knowledge")
data class KnowledgeProperties(
    val baseUrl: String = "http://rag-query-apptolast-rag-query.platform.svc.cluster.local",
    val connectTimeout: Duration = Duration.ofMillis(500),
    val readTimeout: Duration = Duration.ofMillis(2_000),
    val defaultTopK: Int = 5,
    val minScore: Double = 0.55,
)
