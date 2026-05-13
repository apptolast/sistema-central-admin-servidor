package com.apptolast.ragquery.application.service

import com.apptolast.ragquery.application.port.inbound.QueryKnowledgeUseCase
import com.apptolast.ragquery.application.port.outbound.VectorSearchPort
import com.apptolast.ragquery.config.RagQueryProperties
import com.apptolast.ragquery.domain.QueryAnswer
import org.springframework.stereotype.Service

@Service
class QueryKnowledgeService(
    private val vector: VectorSearchPort,
    private val props: RagQueryProperties,
) : QueryKnowledgeUseCase {

    override fun query(question: String, topK: Int?): QueryAnswer {
        require(question.isNotBlank()) { "question must not be blank" }
        val k = (topK ?: props.topK).coerceIn(1, 20)
        val chunks = vector.search(question, k)
        val maxScore = chunks.maxOfOrNull { it.score } ?: 0.0
        val confidence = if (maxScore >= props.minScore) {
            QueryAnswer.Confidence.HIGH
        } else {
            QueryAnswer.Confidence.LOW_NO_EVIDENCE
        }
        return QueryAnswer(question, chunks, confidence)
    }
}
