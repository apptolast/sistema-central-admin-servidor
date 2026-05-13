package com.apptolast.platform.inventory.integration

import com.apptolast.platform.inventory.application.port.outbound.InventoryRepository
import com.apptolast.platform.inventory.application.service.InventoryQueryService
import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.infrastructure.web.InventoryController
import com.apptolast.platform.inventory.infrastructure.web.dto.PodDetailDto
import com.apptolast.platform.knowledge.application.port.inbound.QueryKnowledgePort
import com.apptolast.platform.knowledge.infrastructure.KnowledgeProperties
import com.apptolast.platform.knowledge.infrastructure.RestKnowledgeClient
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URI
import java.time.Instant

/**
 * Test E2E del flujo Wave-C completo: cluster-watcher → inventory →
 * knowledge → rag-query → cita resuelta. Verifica el contrato anti-
 * hallucination a través de la pila completa SIN levantar Spring context
 * (bloqueado por KGP 2.3.21 bug; ver `InventoryControllerTest.kt`).
 *
 * Patrón:
 *  - InventoryRepository mockeado (no DB en test).
 *  - QueryKnowledgePort = RestKnowledgeClient real apuntado a un
 *    MockRestServiceServer que simula al microservicio `rag-query`.
 *  - InventoryController invocado directamente (sin MockMvc) — la única
 *    diferencia vs HTTP real es el binding REST, que Spring MVC test ya
 *    cubre exhaustivamente en su propia suite.
 *
 * Cubre el contrato Wave-C C5:
 *   1. Pod existe + rag-query devuelve citations → HTTP 200 con runbooks
 *   2. Pod existe + rag-query devuelve LOW_NO_EVIDENCE → HTTP 200 sin runbooks
 *   3. Pod existe + rag-query devuelve 5xx → HTTP 200 sin runbooks
 *      (anti-hallucination: NUNCA propaga error a inventory)
 *   4. Pod existe + rag-query devuelve cita malformada → HTTP 200, cita
 *      malformada filtrada del array
 *   5. Pod NO existe → HTTP 404, NO se llama a rag-query
 */
class InventoryKnowledgeFlowE2ETest {

    private val ragQueryBase = "http://rag-query.platform.svc:8082"
    private val ref = ResourceRef(ResourceKind.POD, "n8n", "postgres-n8n-0")

    private val repository = mockk<InventoryRepository>(relaxed = true)
    private lateinit var ragServer: MockRestServiceServer
    private lateinit var controller: InventoryController

    @BeforeEach
    fun setup() {
        val builder = RestClient.builder().baseUrl(ragQueryBase)
        ragServer = MockRestServiceServer.bindTo(builder).build()
        val knowledgePort: QueryKnowledgePort = RestKnowledgeClient(
            builder.build(),
            KnowledgeProperties(baseUrl = ragQueryBase),
        )
        val service = InventoryQueryService(repository, knowledgePort)
        controller = InventoryController(service)
    }

    private fun aPod(): Pod = Pod(
        ref = ref,
        resourceVersion = "100",
        observedGeneration = 1,
        phase = PodPhase.RUNNING,
        nodeName = "apptolastserver",
        podIp = "10.244.198.10",
        containers = listOf(
            Container("postgres", "postgres:16.10", true, 0, ContainerState.RUNNING),
        ),
        owner = null,
        labels = emptyMap(),
        annotations = emptyMap(),
        observedAt = Instant.parse("2026-05-13T00:00:00Z"),
    )

    @Test
    fun `pod detail with evidence returns 200 plus runbooks`() {
        every { repository.findPod(ref) } returns aPod()
        ragServer.expect(requestTo(URI("$ragQueryBase/api/v1/rag/query")))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "CITED",
                      "citations": [
                        {"sourcePath":"docs/runbooks/RB-10_PG_CONNECTIONS_HIGH.md","section":"1-sintomas","sha":"abc1234"},
                        {"sourcePath":"docs/runbooks/RB-13_PG_TXID_WRAPAROUND.md","section":"fix","sha":"def5678"}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response: ResponseEntity<PodDetailDto> = controller.getPod("n8n", "postgres-n8n-0")
        val body = response.body!!

        response.statusCode shouldBe HttpStatus.OK
        body.pod.name shouldBe "postgres-n8n-0"
        body.pod.namespace shouldBe "n8n"
        body.relatedRunbooks shouldHaveSize 2
        body.relatedRunbooks[0].sourcePath shouldBe "docs/runbooks/RB-10_PG_CONNECTIONS_HIGH.md"
        body.relatedRunbooks[0].citation shouldBe
            "[source: docs/runbooks/RB-10_PG_CONNECTIONS_HIGH.md#1-sintomas@abc1234]"
        ragServer.verify()
    }

    @Test
    fun `pod detail with LOW_NO_EVIDENCE returns 200 with empty runbooks (anti-hallucination)`() {
        every { repository.findPod(ref) } returns aPod()
        ragServer.expect(requestTo(URI("$ragQueryBase/api/v1/rag/query")))
            .andRespond(
                withSuccess(
                    """{"status":"LOW_NO_EVIDENCE","citations":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = controller.getPod("n8n", "postgres-n8n-0")

        response.statusCode shouldBe HttpStatus.OK
        response.body!!.relatedRunbooks.shouldBeEmpty()
        ragServer.verify()
    }

    @Test
    fun `pod detail with rag-query 5xx returns 200 with empty runbooks (NEVER propagates)`() {
        every { repository.findPod(ref) } returns aPod()
        ragServer.expect(requestTo(URI("$ragQueryBase/api/v1/rag/query")))
            .andRespond(withServerError())

        // El test NO captura excepción — confirma que el caller NO recibe error.
        val response = controller.getPod("n8n", "postgres-n8n-0")

        response.statusCode shouldBe HttpStatus.OK
        response.body!!.pod.name shouldBe "postgres-n8n-0"
        response.body!!.relatedRunbooks.shouldBeEmpty()
        ragServer.verify()
    }

    @Test
    fun `pod detail filters malformed citations from rag-query response`() {
        every { repository.findPod(ref) } returns aPod()
        ragServer.expect(requestTo(URI("$ragQueryBase/api/v1/rag/query")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "CITED",
                      "citations": [
                        {"sourcePath":"docs/x.md","section":"s","sha":"NOT-HEX"},
                        {"sourcePath":"docs/runbooks/RB-99.md","section":"how-to","sha":"feedbac"}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = controller.getPod("n8n", "postgres-n8n-0")

        response.body!!.relatedRunbooks shouldHaveSize 1
        response.body!!.relatedRunbooks[0].sourcePath shouldBe "docs/runbooks/RB-99.md"
        ragServer.verify()
    }

    @Test
    fun `pod not found returns 404 and does NOT call rag-query`() {
        every { repository.findPod(ref) } returns null
        // No ragServer.expect() — si hiciera HTTP, verify() fallaría.

        val response = controller.getPod("n8n", "postgres-n8n-0")

        response.statusCode shouldBe HttpStatus.NOT_FOUND
        ragServer.verify()  // ZERO interactions — ahorro y trazabilidad.
    }

    @Test
    fun `pod detail with 404 from rag-query returns 200 with empty runbooks`() {
        // 404 ≠ "pod no existe en inventory". Aquí rag-query no encuentra docs.
        every { repository.findPod(ref) } returns aPod()
        ragServer.expect(requestTo(URI("$ragQueryBase/api/v1/rag/query")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val response = controller.getPod("n8n", "postgres-n8n-0")

        response.statusCode shouldBe HttpStatus.OK
        response.body!!.relatedRunbooks.shouldBeEmpty()
        ragServer.verify()
    }
}
