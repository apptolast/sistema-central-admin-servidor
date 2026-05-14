package com.apptolast.ragquery.infrastructure.vector

import com.apptolast.ragquery.application.port.outbound.VectorSearchPort
import com.apptolast.ragquery.domain.Citation
import com.apptolast.ragquery.domain.RetrievedChunk
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adapter pgvector — usa Spring AI VectorStore para hacer similarity search.
 *
 * Resuelve el VectorStore en tiempo de uso. No usamos @ConditionalOnBean en
 * este @Component porque puede evaluarse antes de que la autoconfiguración de
 * Spring AI registre PgVectorStore, dejando la app en fallback aunque pgvector
 * esté configurado.
 *
 * El metadata esperado en cada Document de pgvector (lo escribe rag-ingestor):
 *   path: String
 *   section: String
 *   sha: String
 */
@Component
class PgVectorSearchAdapter(
    private val vectorStoreProvider: ObjectProvider<VectorStore>,
) : VectorSearchPort {

    private val log = LoggerFactory.getLogger(PgVectorSearchAdapter::class.java)
    private val missingVectorStoreLogged = AtomicBoolean(false)

    override fun search(question: String, topK: Int): List<RetrievedChunk> {
        val vectorStore = vectorStoreProvider.getIfAvailable()
        if (vectorStore == null) {
            if (missingVectorStoreLogged.compareAndSet(false, true)) {
                log.warn(
                    "pgvector NOT configured — rag-query running with empty index. " +
                        "All queries will return LOW_NO_EVIDENCE.",
                )
            }
            return emptyList()
        }

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
                score = doc.score ?: score,
            )
        }
    }
}
