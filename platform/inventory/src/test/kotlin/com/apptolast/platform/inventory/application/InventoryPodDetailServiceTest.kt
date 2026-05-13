package com.apptolast.platform.inventory.application

import com.apptolast.platform.inventory.application.port.outbound.InventoryRepository
import com.apptolast.platform.inventory.application.service.InventoryQueryService
import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.knowledge.application.port.inbound.QueryKnowledgePort
import com.apptolast.platform.knowledge.domain.model.Citation
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Tests del wiring inventory ↔ knowledge para [InventoryQueryService.getPodDetail].
 *
 * Cubre las 3 reglas anti-hallucination que documenta ADR-0007:
 *   1. Knowledge devuelve citations → DTO las incluye.
 *   2. Knowledge devuelve emptyList → DTO con relatedRunbooks vacío (sin alucinar).
 *   3. Knowledge lanza excepción → defensa extra; DTO con relatedRunbooks vacío.
 *   4. Knowledge no wireado (null) → DTO con relatedRunbooks vacío.
 *   5. Pod inexistente → null, sin llamar a knowledge.
 */
class InventoryPodDetailServiceTest {

    private val repository = mockk<InventoryRepository>(relaxed = true)
    private val knowledge = mockk<QueryKnowledgePort>(relaxed = true)
    private val ref = ResourceRef(ResourceKind.POD, "n8n", "postgres-n8n-0")

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
    fun `getPodDetail returns pod plus citations when knowledge has evidence`() {
        every { repository.findPod(ref) } returns aPod()
        every { knowledge.query(any(), any()) } returns listOf(
            Citation("docs/runbooks/RB-10_PG_CONNECTIONS_HIGH.md", "1-sintomas", "abc1234"),
            Citation("docs/runbooks/RB-13_PG_TXID_WRAPAROUND.md", "fix", "def5678"),
        )

        val service = InventoryQueryService(repository, knowledge)
        val detail = service.getPodDetail(ref)!!

        detail.pod.ref shouldBe ref
        detail.relatedRunbooks shouldHaveSize 2
        detail.relatedRunbooks[0].sourcePath shouldBe "docs/runbooks/RB-10_PG_CONNECTIONS_HIGH.md"
        // Verificar que la query incluye namespace + name (relevancia mínima):
        verify { knowledge.query(match { it.contains("postgres-n8n-0") && it.contains("n8n") }, 3) }
    }

    @Test
    fun `getPodDetail returns empty runbooks when knowledge returns empty list`() {
        every { repository.findPod(ref) } returns aPod()
        every { knowledge.query(any(), any()) } returns emptyList()

        val service = InventoryQueryService(repository, knowledge)
        val detail = service.getPodDetail(ref)!!

        detail.pod.ref shouldBe ref
        detail.relatedRunbooks.shouldBeEmpty()
    }

    @Test
    fun `getPodDetail returns empty runbooks when knowledge throws (anti-hallucination)`() {
        every { repository.findPod(ref) } returns aPod()
        every { knowledge.query(any(), any()) } throws RuntimeException("upstream timeout")

        val service = InventoryQueryService(repository, knowledge)
        val detail = service.getPodDetail(ref)

        detail!!.pod.ref shouldBe ref
        // El test no falla: pod-detail sigue funcionando aunque knowledge esté roto.
        detail.relatedRunbooks.shouldBeEmpty()
    }

    @Test
    fun `getPodDetail returns empty runbooks when no knowledge port wired`() {
        every { repository.findPod(ref) } returns aPod()

        // knowledge = null simula entorno dev sin rag-query desplegado.
        val service = InventoryQueryService(repository, knowledge = null)
        val detail = service.getPodDetail(ref)!!

        detail.relatedRunbooks.shouldBeEmpty()
    }

    @Test
    fun `getPodDetail returns null without touching knowledge when pod not found`() {
        every { repository.findPod(ref) } returns null

        val service = InventoryQueryService(repository, knowledge)
        val detail = service.getPodDetail(ref)

        detail shouldBe null
        // Si el pod no existe, NO llamamos a knowledge — ahorro innecesario.
        verify(exactly = 0) { knowledge.query(any(), any()) }
    }
}
