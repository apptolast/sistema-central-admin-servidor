package com.apptolast.platform.inventory.infrastructure

import com.apptolast.platform.inventory.application.port.inbound.PodFilter
import com.apptolast.platform.inventory.application.port.inbound.QueryInventoryUseCase
import com.apptolast.platform.inventory.domain.model.Container
import com.apptolast.platform.inventory.domain.model.ContainerState
import com.apptolast.platform.inventory.domain.model.Pod
import com.apptolast.platform.inventory.domain.model.PodPhase
import com.apptolast.platform.inventory.domain.model.ResourceKind
import com.apptolast.platform.inventory.domain.model.ResourceRef
import com.apptolast.platform.inventory.infrastructure.web.InventoryController
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(InventoryController::class)
class InventoryControllerTest(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    lateinit var query: QueryInventoryUseCase

    @Test
    fun `GET pods returns 200 and json array`() {
        every { query.listPods(any()) } returns listOf(aPod("default", "pod-1"))

        mockMvc.perform(get("/api/v1/inventory/pods"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].namespace").value("default"))
            .andExpect(jsonPath("$[0].name").value("pod-1"))
            .andExpect(jsonPath("$[0].ready").value(true))
            .andExpect(jsonPath("$[0].restarts").value(0))
    }

    @Test
    fun `GET pods with filter passes namespace and phase`() {
        every {
            query.listPods(PodFilter(namespace = "n8n", phase = "RUNNING"))
        } returns listOf(aPod("n8n", "n8n-prod"))

        mockMvc.perform(get("/api/v1/inventory/pods?namespace=n8n&phase=RUNNING"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].namespace").value("n8n"))
    }

    @Test
    fun `GET pod by ns and name returns 404 when not found`() {
        every {
            query.getPod(ResourceRef(ResourceKind.POD, "x", "missing"))
        } returns null

        mockMvc.perform(get("/api/v1/inventory/pods/x/missing"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET pod by ns and name returns 200 when found`() {
        every {
            query.getPod(ResourceRef(ResourceKind.POD, "default", "pod-1"))
        } returns aPod("default", "pod-1")

        mockMvc.perform(get("/api/v1/inventory/pods/default/pod-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("pod-1"))
    }

    private fun aPod(namespace: String, name: String) = Pod(
        ref = ResourceRef(ResourceKind.POD, namespace, name),
        resourceVersion = "1",
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
        labels = mapOf("app" to "demo"),
        annotations = emptyMap(),
        observedAt = Instant.parse("2026-05-13T00:00:00Z"),
    )
}
