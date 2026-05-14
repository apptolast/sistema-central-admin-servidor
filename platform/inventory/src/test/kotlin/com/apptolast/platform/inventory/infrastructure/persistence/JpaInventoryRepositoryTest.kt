package com.apptolast.platform.inventory.infrastructure.persistence

import com.apptolast.platform.inventory.application.port.outbound.SaveOutcome
import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.infrastructure.persistence.entity.PodEntity
import com.apptolast.platform.inventory.infrastructure.persistence.repository.CertificateJpaRepository
import com.apptolast.platform.inventory.infrastructure.persistence.repository.IngressJpaRepository
import com.apptolast.platform.inventory.infrastructure.persistence.repository.PodJpaRepository
import com.apptolast.platform.inventory.infrastructure.persistence.repository.PvcJpaRepository
import com.apptolast.platform.inventory.infrastructure.persistence.repository.ServiceJpaRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class JpaInventoryRepositoryTest {

    private val podRepo = mockk<PodJpaRepository>()
    private val repository = JpaInventoryRepository(
        podRepo = podRepo,
        serviceRepo = mockk<ServiceJpaRepository>(relaxed = true),
        ingressRepo = mockk<IngressJpaRepository>(relaxed = true),
        pvcRepo = mockk<PvcJpaRepository>(relaxed = true),
        certRepo = mockk<CertificateJpaRepository>(relaxed = true),
        mapper = InventoryJpaMapper(jacksonObjectMapper().findAndRegisterModules()),
    )

    @Test
    fun `savePod restores soft-deleted row instead of inserting duplicate namespace name`() {
        val deleted = aPodEntity(deletedAt = Instant.parse("2026-05-13T00:00:00Z"))
        val saved = slot<PodEntity>()
        every { podRepo.findByNamespaceAndName("default", "pod-1") } returns deleted
        every { podRepo.save(capture(saved)) } answers { firstArg() }

        val outcome = repository.savePod(aPod(resourceVersion = "101"))

        outcome shouldBe SaveOutcome.Updated
        saved.captured.id shouldBe deleted.id
        saved.captured.deletedAt shouldBe null
        saved.captured.resourceVersion shouldBe "101"
        verify(exactly = 0) { podRepo.findByNamespaceAndNameAndDeletedAtIsNull(any(), any()) }
    }

    @Test
    fun `savePod keeps active row unchanged when resourceVersion already observed`() {
        val active = aPodEntity(resourceVersion = "100", observedAt = Instant.parse("2026-05-13T00:00:00Z"))
        val saved = slot<PodEntity>()
        every { podRepo.findByNamespaceAndName("default", "pod-1") } returns active
        every { podRepo.save(capture(saved)) } answers { firstArg() }

        val outcome = repository.savePod(aPod(resourceVersion = "100", observedAt = Instant.parse("2026-05-14T00:00:00Z")))

        outcome shouldBe SaveOutcome.Unchanged
        saved.captured.id shouldBe active.id
        saved.captured.observedAt shouldBe Instant.parse("2026-05-14T00:00:00Z")
    }

    private fun aPod(
        resourceVersion: String = "100",
        observedAt: Instant = Instant.parse("2026-05-14T00:00:00Z"),
    ): Pod = Pod(
        ref = ResourceRef(ResourceKind.POD, "default", "pod-1"),
        resourceVersion = resourceVersion,
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
        observedAt = observedAt,
    )

    private fun aPodEntity(
        resourceVersion: String = "100",
        observedAt: Instant = Instant.parse("2026-05-13T00:00:00Z"),
        deletedAt: Instant? = null,
    ): PodEntity = PodEntity(
        id = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
        namespace = "default",
        name = "pod-1",
        resourceVersion = resourceVersion,
        observedGeneration = 1,
        phase = "RUNNING",
        nodeName = "apptolastserver",
        podIp = "10.244.198.10",
        containers = """[{"name":"app","image":"nginx:1.27","ready":true,"restartCount":0,"state":"RUNNING"}]""",
        ownerKind = null,
        ownerName = null,
        labels = """{"app":"test"}""",
        annotations = "{}",
        observedAt = observedAt,
        deletedAt = deletedAt,
    )
}
