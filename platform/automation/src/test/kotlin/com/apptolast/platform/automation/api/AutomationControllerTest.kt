package com.apptolast.platform.automation.api

import com.apptolast.platform.automation.application.port.outbound.CommandExecutor
import com.apptolast.platform.automation.application.port.outbound.ExecutionOutcome
import com.apptolast.platform.automation.application.service.CommandRejectedException
import com.apptolast.platform.automation.application.service.SafeOpsKernel
import com.apptolast.platform.automation.domain.model.SafeCommand
import com.apptolast.platform.automation.domain.model.Whitelist
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * Tests directos del controller — sin MockMvc. Razón: spring-modulith 2.0.1
 * arrastra spring-core 6.1 mientras spring-boot-starter-test usa 6.2, lo que
 * produce NoClassDefFoundError MimeType$SpecificityComparator al inicializar
 * MappingJackson2HttpMessageConverter. Mismo problema documentado en
 * InventoryControllerTest. Defer Phase 2 (resolver BOM).
 *
 * Invocación directa cubre el mismo contrato: payload DTO → SafeCommand →
 * SafeOpsKernel → ExecutionOutcome o CommandRejectedException.
 */
class AutomationControllerTest {

    private val executor = object : CommandExecutor {
        override fun execute(command: SafeCommand, timeout: Duration) =
            ExecutionOutcome(command, 0, "ok", "", 5L)
    }

    private fun controllerWith(whitelist: Whitelist): AutomationController =
        AutomationController(SafeOpsKernel(whitelist, executor))

    @Test
    fun `run kubectl-read on READ_ONLY whitelist returns outcome 0`() {
        val outcome = controllerWith(Whitelist.READ_ONLY).run(
            AutomationRunRequest.KubectlReadDto("get", "pods", "n8n", null),
        )
        outcome.exitCode shouldBe 0
        outcome.command.kind shouldBe "kubectl-read"
    }

    @Test
    fun `run helm-rollback rejected by READ_ONLY throws CommandRejectedException`() {
        val ex = assertThrows<CommandRejectedException> {
            controllerWith(Whitelist.READ_ONLY).run(
                AutomationRunRequest.HelmRollbackDto("n8n-prod", "n8n", 11),
            )
        }
        ex.command.kind shouldBe "helm-rollback"
    }

    @Test
    fun `run helm-rollback accepted by permissive whitelist`() {
        val outcome = controllerWith(Whitelist.ALLOW_TRIGGERS_AND_ROLLBACK).run(
            AutomationRunRequest.HelmRollbackDto("n8n-prod", "n8n", 11),
        )
        outcome.exitCode shouldBe 0
    }

    @Test
    fun `payload with shell metachars in name throws IllegalArgumentException at DTO mapping`() {
        assertThrows<IllegalArgumentException> {
            AutomationRunRequest.KubectlReadDto("get", "pods", "n8n", "foo;rm -rf /").toCommand()
        }
    }

    @Test
    fun `handleRejected returns structured error map`() {
        val controller = controllerWith(Whitelist.READ_ONLY)
        val ex = CommandRejectedException(
            SafeCommand.HelmRollback("n8n-prod", "n8n", 11),
            "kind not in whitelist",
        )
        val body = controller.handleRejected(ex)
        body["error"] shouldBe "REJECTED"
        body["kind"] shouldBe "helm-rollback"
        body["reason"] shouldBe "kind not in whitelist"
    }
}
