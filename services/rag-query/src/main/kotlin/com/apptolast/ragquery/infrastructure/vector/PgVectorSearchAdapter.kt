package com.apptolast.ragquery.infrastructure.vector

import com.apptolast.ragquery.application.port.outbound.VectorSearchPort
import com.apptolast.ragquery.domain.Citation
import com.apptolast.ragquery.domain.RetrievedChunk
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

/**
 * Adapter pgvector — usa Spring AI VectorStore para hacer similarity search.
 *
 * Sólo se activa si hay un VectorStore en el contexto (configurado por
 * spring-ai-starter-vector-store-pgvector). Si no, se usa el fallback
 * [StubVectorSearchAdapter] que devuelve lista vacía para que la app arranque
 * en entornos sin Postgres + pgvector (CI, dev local sin DB).
 *
 * El metadata esperado en cada Document de pgvector (lo escribe rag-ingestor):
 *   path: String
 *   section: String
 *   sha: String
 */
@Component
@ConditionalOnBean(VectorStore::class)
class PgVectorSearchAdapter(
    private val vectorStore: VectorStore,
) : VectorSearchPort {

    private val log = LoggerFactory.getLogger(PgVectorSearchAdapter::class.java)

    override fun search(question: String, topK: Int): List<RetrievedChunk> {
        val request = SearchRequest.builder()
            .query(question)
            .topK(topK)
            .build()
        val docs = vectorStore.similaritySearch(request) ?: emptyList()
        log.debug("pgvector returned {} docs for question of length {}", docs.size, question.length)
        return docs.mapNotNull { doc ->
            val md = doc.metadata
            val path = md["path"]?.toString() ?: return@mapNotNull null
            val section = md["section"]?.toString() ?: "untitled"
            val sha = md["sha"]?.toString() ?: "unknown"
            val score = (md["distance"] as? Number)?.toDouble()?.let { 1.0 - it } ?: 0.0
            RetrievedChunk(
                content = doc.text ?: "",
                citation = Citation(path, section, sha),
                score = score,
            )
        }
    }
}
