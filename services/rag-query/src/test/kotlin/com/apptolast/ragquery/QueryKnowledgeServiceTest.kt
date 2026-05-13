package com.apptolast.ragquery

import com.apptolast.ragquery.application.port.outbound.VectorSearchPort
import com.apptolast.ragquery.application.service.QueryKnowledgeService
import com.apptolast.ragquery.config.RagQueryProperties
import com.apptolast.ragquery.domain.Citation
import com.apptolast.ragquery.domain.QueryAnswer
import com.apptolast.ragquery.domain.RetrievedChunk
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QueryKnowledgeServiceTest {

    private val props = RagQueryProperties()

    @Test
    fun `HIGH confidence when top score above min`() {
        val svc = QueryKnowledgeService(
            vector = stub(
                RetrievedChunk("contenido", Citation("docs/runbooks/RB-01.md", "remediacion", "a3f1b2c"), 0.82),
            ),
            props = props,
        )
        val answer = svc.query("¿cómo libero disco?")
        answer.confidence shouldBe QueryAnswer.Confidence.HIGH
        answer.chunks.size shouldBe 1
        answer.chunks.first().citation.toString() shouldContain "[source: docs/runbooks/RB-01.md#remediacion@a3f1b2c]"
    }

    @Test
    fun `LOW_NO_EVIDENCE when top score below min`() {
        val svc = QueryKnowledgeService(
            vector = stub(
                RetrievedChunk("contenido marginal", Citation("docs/x.md", "intro", "deadbee"), 0.42),
            ),
            props = props,
        )
        val answer = svc.query("¿qué hago?")
        answer.confidence shouldBe QueryAnswer.Confidence.LOW_NO_EVIDENCE
    }

    @Test
    fun `LOW_NO_EVIDENCE when no chunks returned`() {
        val svc = QueryKnowledgeService(stub(), props)
        val answer = svc.query("desconocido")
        answer.confidence shouldBe QueryAnswer.Confidence.LOW_NO_EVIDENCE
        answer.chunks.size shouldBe 0
    }

    @Test
    fun `blank question throws`() {
        val svc = QueryKnowledgeService(stub(), props)
        assertThrows<IllegalArgumentException> { svc.query("   ") }
    }

    @Test
    fun `topK is clamped to 1-20`() {
        val captured = mutableListOf<Int>()
        val svc = QueryKnowledgeService(captureK(captured), props)
        svc.query("x", topK = 0)
        svc.query("x", topK = 999)
        svc.query("x", topK = 7)
        captured shouldBe listOf(1, 20, 7)
    }

    private fun stub(vararg chunks: RetrievedChunk): VectorSearchPort =
        object : VectorSearchPort {
            override fun search(question: String, topK: Int): List<RetrievedChunk> = chunks.toList()
        }

    private fun captureK(into: MutableList<Int>): VectorSearchPort =
        object : VectorSearchPort {
            override fun search(question: String, topK: Int): List<RetrievedChunk> {
                into += topK
                return emptyList()
            }
        }
}
