package com.apptolast.clusterwatcher

import com.apptolast.clusterwatcher.config.ClusterWatcherProperties
import com.apptolast.clusterwatcher.k8s.PodInformer
import com.apptolast.clusterwatcher.publisher.HttpEventPublisher
import io.fabric8.kubernetes.api.model.PodBuilder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PodInformerDeleteTest {

    @Test
    fun `publishes delete payload without recursive handler call`() {
        var payload: Map<String, Any?>? = null
        val publisher = mockk<HttpEventPublisher>()
        every { publisher.publish(any()) } answers {
            payload = firstArg()
            Unit
        }
        val informer = PodInformer(
            client = mockk(relaxed = true),
            properties = ClusterWatcherProperties(),
            publisher = publisher,
        )
        val pod = PodBuilder()
            .withNewMetadata()
            .withNamespace("platform")
            .withName("gone-pod")
            .withResourceVersion("42")
            .withGeneration(7L)
            .endMetadata()
            .build()

        informer.publishDelete(pod, deletedFinalStateUnknown = true)

        verify(exactly = 1) { publisher.publish(any()) }
        payload?.get("kind") shouldBe "POD"
        payload?.get("operation") shouldBe "DELETE"
        payload?.get("namespace") shouldBe "platform"
        payload?.get("name") shouldBe "gone-pod"
        payload?.get("deletedFinalStateUnknown") shouldBe true
    }
}
