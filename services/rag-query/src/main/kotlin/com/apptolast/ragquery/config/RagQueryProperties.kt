package com.apptolast.ragquery.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag.query")
data class RagQueryProperties(
    val topK: Int = 5,
    val minScore: Double = 0.60,
    val embeddingsModel: String = "text-embedding-3-small",
    val embeddingDimension: Int = 1536,
)
