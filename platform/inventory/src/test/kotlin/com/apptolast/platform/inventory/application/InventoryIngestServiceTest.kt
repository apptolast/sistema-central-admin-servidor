package com.apptolast.platform.inventory.application

import com.apptolast.platform.inventory.api.events.InventoryEvent
import com.apptolast.platform.inventory.application.port.inbound.IngestResult
import com.apptolast.platform.inventory.application.port.outbound.InventoryEventPublisher
import com.apptolast.platform.inventory.application.port.outbound.InventoryRepository
import com.apptolast.platform.inventory.application.port.outbound.SaveOutcome
import com.apptolast.platform.inventory.application.service.InventoryIngestService
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class InventoryIngestServiceTest {

    private val repository = mockk<InventoryRepository>(relaxed = true)
    private val publisher = mockk<InventoryEventPublisher>(relaxed = true)
    private val service = InventoryIngestService(repository, publisher)

    @Test
    fun `ingest pod returns Created and publishes event when new`() {
        every { repository.savePod(any()) } returns SaveOutcome.Inserted
        val pod = aPod()

        val result = service.ingest(pod)

        result shouldBe IngestResult.Created
        verify(exactly = 1) { publisher.publish(any<InventoryEvent>()) }
    }

    @Test
    fun `ingest pod returns Updated and publishes event when updated`() {
        every { repository.savePod(any()) } returns SaveOutcome.Updated
        val pod = aPod()

        val result = service.ingest(pod)

        result shouldBe IngestResult.Updated
        verify(exactly = 1) { publisher.publish(any<InventoryEvent>()) }
    }

    @Test
    fun `ingest pod returns Unchanged and does NOT publish event when same resourceVersion`() {
        every { repository.savePod(any()) } returns SaveOutcome.Unchanged
        val pod = aPod()

        val result = service.ingest(pod)

        result shouldBe IngestResult.Unchanged
        verify(exactly = 0) { publisher.publish(any<InventoryEvent>()) }
    }

    @Test
    fun `markDeleted returns Rejected when repository says no rows affected`() {
        val ref = ResourceRef(ResourceKind.POD, "ns", "missing")
        every { repository.markDeleted(ref) } returns false

        val result = service.markDeleted(ref)

        result.shouldBeInstanceOf<IngestResult.Rejected>()
    }

    @Test
    fun `markDeleted publishes ResourceDeleted event when row deleted`() {
        val ref = ResourceRef(ResourceKind.POD, "ns", "pod-1")
        every { repository.markDeleted(ref) } returns true

        val result = service.markDeleted(ref)

        result shouldBe IngestResult.Deleted
        verify(exactly = 1) { publisher.publish(any<InventoryEvent>()) }
    }

    private fun aPod(): Pod = Pod(
        ref = ResourceRef(ResourceKind.POD, "default", "pod-1"),
        resourceVersion = "100",
        observedGeneration = 1,
        phase = PodPhase.RUNNING,
        nodeName = "apptolastserver",
        podIp = "10.244.198.10",
        containers = listOf(
            Container(
                name = "app",
                image = "nginx:1.27",
                ready = true,
                restartCount = 0,
                state = ContainerState.RUNNING,
            ),
        ),
        owner = null,
        labels = mapOf("app" to "test"),
        annotations = emptyMap(),
        observedAt = Instant.parse("2026-05-13T00:00:00Z"),
    )
}
