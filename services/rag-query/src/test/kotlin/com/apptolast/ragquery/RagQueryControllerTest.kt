package com.apptolast.ragquery

import com.apptolast.ragquery.api.QueryResponse
import com.apptolast.ragquery.domain.Citation
import com.apptolast.ragquery.domain.QueryAnswer
import com.apptolast.ragquery.domain.RetrievedChunk
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class RagQueryControllerTest {

    @Test
    fun `response body is built only from retrieved evidence`() {
        val response = QueryResponse.from(
            QueryAnswer(
                question = "como se despliega rag-ingestor",
                chunks = listOf(
                    RetrievedChunk(
                        content = "helm upgrade --install rag-ingestor ...",
                        citation = Citation("docs/services/rag-ingestor.md", "despliegue", "abc1234"),
                        score = 0.91,
                    ),
                    RetrievedChunk(
                        content = "kubectl rollout status deploy/rag-ingestor",
                        citation = Citation("docs/services/rag-ingestor.md", "verificacion", "abc1234"),
                        score = 0.83,
                    ),
                ),
                confidence = QueryAnswer.Confidence.HIGH,
            ),
        )

        response.status shouldBe "CITED"
        response.body shouldContain "helm upgrade --install rag-ingestor"
        response.body shouldContain "kubectl rollout status deploy/rag-ingestor"
        response.chunks.size shouldBe 2
        response.citations.size shouldBe 2
    }

    @Test
    fun `low evidence response does not synthesize a body`() {
        val response = QueryResponse.from(
            QueryAnswer(
                question = "desconocido",
                chunks = emptyList(),
                confidence = QueryAnswer.Confidence.LOW_NO_EVIDENCE,
            ),
        )

        response.status shouldBe "LOW_NO_EVIDENCE"
        response.body.shouldBeNull()
    }
}
