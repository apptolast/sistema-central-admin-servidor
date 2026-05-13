package com.apptolast.ragquery.infrastructure.vector

import com.apptolast.ragquery.application.port.outbound.VectorSearchPort
import com.apptolast.ragquery.domain.RetrievedChunk
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

/**
 * Fallback in-memory — usado cuando no hay Postgres + pgvector configurado.
 * Devuelve lista vacía → confidence siempre LOW_NO_EVIDENCE.
 *
 * Esto preserva el contrato (la app arranca, /api/v1/rag/query responde 200)
 * sin fingir respuestas (regla anti-hallucination de Pablo).
 */
@Component
@ConditionalOnMissingBean(VectorStore::class)
class StubVectorSearchAdapter : VectorSearchPort {

    private val log = LoggerFactory.getLogger(StubVectorSearchAdapter::class.java)

    init {
        log.warn(
            "pgvector NOT configured — rag-query running with empty index. " +
                "All queries will return LOW_NO_EVIDENCE.",
        )
    }

    override fun search(question: String, topK: Int): List<RetrievedChunk> = emptyList()
}
