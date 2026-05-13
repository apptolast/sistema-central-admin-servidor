package com.apptolast.platform.knowledge.infrastructure

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.http.HttpMethod
import java.net.URI

/**
 * Tests del cliente RAG con [MockRestServiceServer]. Cubre:
 *  - parseo de respuesta `Cited` → lista de citations
 *  - confidence gate (LOW_NO_EVIDENCE → emptyList)
 *  - graceful degradation en 5xx / timeout
 *  - filtrado de citations malformadas (no rompe el batch)
 *
 * MockRestServiceServer obliga a usar un RestClient construido sobre el
 * RestTemplate que el server mockea; aquí construimos el adapter
 * directamente sin Spring context para mantener el test rápido y barato.
 */
class RestKnowledgeClientTest {

    private val properties = KnowledgeProperties(
        baseUrl = "http://rag-query.platform.svc:8082",
    )
    private lateinit var restClient: RestClient
    private lateinit var server: MockRestServiceServer
    private lateinit var client: RestKnowledgeClient

    @BeforeEach
    fun setup() {
        val builder = RestClient.builder().baseUrl(properties.baseUrl)
        // MockRestServiceServer.bindTo(RestClient.Builder) mockea sobre el builder.
        server = MockRestServiceServer.bindTo(builder).build()
        restClient = builder.build()
        client = RestKnowledgeClient(restClient, properties)
    }

    @Test
    fun `cited response returns parsed citations in order`() {
        server.expect(requestTo(URI("http://rag-query.platform.svc:8082/api/v1/rag/query")))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "CITED",
                      "citations": [
                        {"sourcePath": "docs/runbooks/RB-01.md", "section": "1-sintomas", "sha": "abc1234"},
                        {"sourcePath": "docs/runbooks/RB-02.md", "section": "fix", "sha": "def5678"}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.query("pod foo error", topK = 3)

        result shouldHaveSize 2
        result[0].sourcePath shouldBe "docs/runbooks/RB-01.md"
        result[0].toMarkdown() shouldBe "[source: docs/runbooks/RB-01.md#1-sintomas@abc1234]"
        server.verify()
    }

    @Test
    fun `low confidence response returns empty list (anti-hallucination)`() {
        server.expect(requestTo(URI("http://rag-query.platform.svc:8082/api/v1/rag/query")))
            .andRespond(
                withSuccess(
                    """{"status":"LOW_NO_EVIDENCE","citations":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        client.query("question with no answer") shouldBe emptyList()
        server.verify()
    }

    @Test
    fun `5xx error returns empty list and does not throw`() {
        server.expect(requestTo(URI("http://rag-query.platform.svc:8082/api/v1/rag/query")))
            .andRespond(withServerError())

        // No assertThrows — confirma que NO propaga.
        client.query("anything") shouldBe emptyList()
        server.verify()
    }

    @Test
    fun `404 error returns empty list and does not throw`() {
        server.expect(requestTo(URI("http://rag-query.platform.svc:8082/api/v1/rag/query")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        client.query("anything") shouldBe emptyList()
        server.verify()
    }

    @Test
    fun `malformed citation entries are skipped not propagated`() {
        // Una cita con sha inválido y otra completa. La completa debe sobrevivir.
        server.expect(requestTo(URI("http://rag-query.platform.svc:8082/api/v1/rag/query")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "CITED",
                      "citations": [
                        {"sourcePath": "docs/x.md", "section": "s", "sha": "NOT-HEX"},
                        {"sourcePath": "docs/ok.md", "section": "intro", "sha": "1234567"}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.query("q")

        result shouldHaveSize 1
        result[0].sourcePath shouldBe "docs/ok.md"
        server.verify()
    }

    @Test
    fun `blank question short-circuits and does not hit network`() {
        // No expectations registered — si hiciera HTTP, server.verify() fallaría.
        client.query("   ") shouldBe emptyList()
        client.query("") shouldBe emptyList()
        server.verify()
    }
}
