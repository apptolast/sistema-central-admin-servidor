package com.apptolast.clusterwatcher

import com.apptolast.clusterwatcher.config.ClusterWatcherProperties
import com.apptolast.clusterwatcher.k8s.PvcInformer
import com.apptolast.clusterwatcher.publisher.HttpEventPublisher
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Verifica el parser de Quantity (Ki/Mi/Gi/Ti) sin necesidad de un cliente fabric8 real.
 */
class PvcInformerQuantityTest {

    private val informer = PvcInformer(
        client = mockk(relaxed = true),
        properties = ClusterWatcherProperties(),
        publisher = mockk<HttpEventPublisher>(relaxed = true),
    )

    @Test
    fun `parses 5Gi`() {
        informer.parseQuantityToBytes("5Gi") shouldBe 5L * 1024 * 1024 * 1024
    }

    @Test
    fun `parses 500Mi`() {
        informer.parseQuantityToBytes("500Mi") shouldBe 500L * 1024 * 1024
    }

    @Test
    fun `parses 10G (decimal SI)`() {
        informer.parseQuantityToBytes("10G") shouldBe 10L * 1000 * 1000 * 1000
    }

    @Test
    fun `parses raw bytes`() {
        informer.parseQuantityToBytes("1073741824") shouldBe 1073741824L
    }

    @Test
    fun `returns 0 on invalid input`() {
        informer.parseQuantityToBytes("not-a-quantity") shouldBe 0L
    }
}
