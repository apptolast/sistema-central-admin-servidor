package com.apptolast.ragquery.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag.query")
data class RagQueryProperties(
    val topK: Int = 5,
    val minScore: Double = 0.55,
    val embeddingsModel: String = "text-embedding-3-large",
    val embeddingDimension: Int = 3072,
)
