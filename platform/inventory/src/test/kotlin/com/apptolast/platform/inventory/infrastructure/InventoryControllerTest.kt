package com.apptolast.platform.inventory.infrastructure

import com.apptolast.platform.inventory.application.port.inbound.PodDetail
import com.apptolast.platform.inventory.application.port.inbound.PodFilter
import com.apptolast.platform.inventory.application.port.inbound.QueryInventoryUseCase
import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.infrastructure.web.InventoryController
import com.apptolast.platform.knowledge.domain.model.Citation
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class InventoryControllerTest {

    private val query = mockk<QueryInventoryUseCase>(relaxed = true)
    private val controller = InventoryController(query)

    @Test
    fun `listPods forwards filters and maps domain pods to dto`() {
        every { query.listPods(PodFilter(namespace = "n8n", phase = "RUNNING")) } returns listOf(aPod())

        val response = controller.listPods(namespace = "n8n", phase = "RUNNING")

        response shouldHaveSize 1
        response[0].namespace shouldBe "n8n"
        response[0].name shouldBe "postgres-n8n-0"
        response[0].ready shouldBe true
        response[0].restarts shouldBe 1
        verify(exactly = 1) { query.listPods(PodFilter(namespace = "n8n", phase = "RUNNING")) }
    }

    @Test
    fun `getPod returns 404 when use case has no pod detail`() {
        every { query.getPodDetail(ResourceRef(ResourceKind.POD, "n8n", "missing")) } returns null

        val response = controller.getPod(namespace = "n8n", name = "missing")

        response.statusCode.value() shouldBe 404
    }

    @Test
    fun `getPod returns pod detail with canonical runbook citations`() {
        val ref = ResourceRef(ResourceKind.POD, "n8n", "postgres-n8n-0")
        every { query.getPodDetail(ref) } returns PodDetail(
            pod = aPod(),
            relatedRunbooks = listOf(Citation("docs/runbooks/RB-51_POSTGRES_PGDUMP.md", "restore", "abc1234")),
        )

        val response = controller.getPod(namespace = "n8n", name = "postgres-n8n-0")

        response.statusCode.value() shouldBe 200
        response.body!!.pod.name shouldBe "postgres-n8n-0"
        response.body!!.relatedRunbooks shouldHaveSize 1
        response.body!!.relatedRunbooks[0].citation shouldBe
            "[source: docs/runbooks/RB-51_POSTGRES_PGDUMP.md#restore@abc1234]"
    }

    private fun aPod(): Pod = Pod(
        ref = ResourceRef(ResourceKind.POD, "n8n", "postgres-n8n-0"),
        resourceVersion = "100",
        observedGeneration = 1,
        phase = PodPhase.RUNNING,
        nodeName = "apptolastserver",
        podIp = "10.244.198.10",
        containers = listOf(
            Container(
                name = "postgres",
                image = "postgres:16.10",
                ready = true,
                restartCount = 1,
                state = ContainerState.RUNNING,
            ),
        ),
        owner = null,
        labels = mapOf("app" to "postgres"),
        annotations = emptyMap(),
        observedAt = Instant.parse("2026-05-13T00:00:00Z"),
    )
}
