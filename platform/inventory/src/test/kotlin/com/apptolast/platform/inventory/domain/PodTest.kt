package com.apptolast.platform.inventory.domain

import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class PodTest {

    @Test
    fun `isReady is true when phase=RUNNING and all containers are ready`() {
        val pod = aPod(
            phase = PodPhase.RUNNING,
            containers = listOf(aContainer(ready = true), aContainer(name = "c2", ready = true)),
        )
        pod.isReady() shouldBe true
    }

    @Test
    fun `isReady is false when any container is not ready`() {
        val pod = aPod(
            phase = PodPhase.RUNNING,
            containers = listOf(aContainer(ready = true), aContainer(name = "c2", ready = false)),
        )
        pod.isReady() shouldBe false
    }

    @Test
    fun `isReady is false when phase is not RUNNING regardless of containers`() {
        val pod = aPod(
            phase = PodPhase.PENDING,
            containers = listOf(aContainer(ready = true)),
        )
        pod.isReady() shouldBe false
    }

    @Test
    fun `totalRestarts sums restarts across containers`() {
        val pod = aPod(
            containers = listOf(
                aContainer(restartCount = 3),
                aContainer(name = "c2", restartCount = 7),
            ),
        )
        pod.totalRestarts() shouldBe 10
    }

    @Test
    fun `Pod constructor rejects ResourceRef with wrong kind`() {
        val wrongRef = ResourceRef(ResourceKind.SERVICE, "ns", "name")
        val ex = assertThrows<IllegalArgumentException> {
            Pod(
                ref = wrongRef,
                resourceVersion = "1",
                observedGeneration = 1,
                phase = PodPhase.RUNNING,
                nodeName = null,
                podIp = null,
                containers = emptyList(),
                owner = null,
                labels = emptyMap(),
                annotations = emptyMap(),
                observedAt = Instant.now(),
            )
        }
        ex.message?.contains("kind=POD") shouldBe true
    }

    @Test
    fun `Container constructor rejects negative restart count`() {
        val ex = assertThrows<IllegalArgumentException> {
            Container(
                name = "c1",
                image = "nginx:1.27",
                ready = true,
                restartCount = -1,
                state = ContainerState.RUNNING,
            )
        }
        ex.message?.contains("restartCount") shouldBe true
    }

    private fun aPod(
        phase: PodPhase = PodPhase.RUNNING,
        containers: List<Container> = listOf(aContainer()),
    ) = Pod(
        ref = ResourceRef(ResourceKind.POD, "default", "pod-1"),
        resourceVersion = "1",
        observedGeneration = 1,
        phase = phase,
        nodeName = "node-a",
        podIp = "10.0.0.1",
        containers = containers,
        owner = null,
        labels = mapOf("app" to "x"),
        annotations = emptyMap(),
        observedAt = Instant.parse("2026-05-13T00:00:00Z"),
    )

    private fun aContainer(
        name: String = "c1",
        ready: Boolean = true,
        restartCount: Int = 0,
    ) = Container(
        name = name,
        image = "nginx:1.27",
        ready = ready,
        restartCount = restartCount,
        state = ContainerState.RUNNING,
    )
}
