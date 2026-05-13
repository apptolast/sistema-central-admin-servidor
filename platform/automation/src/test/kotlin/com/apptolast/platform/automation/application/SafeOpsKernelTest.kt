package com.apptolast.platform.automation.application

import com.apptolast.platform.automation.application.port.outbound.CommandExecutor
import com.apptolast.platform.automation.application.port.outbound.ExecutionOutcome
import com.apptolast.platform.automation.application.service.CommandRejectedException
import com.apptolast.platform.automation.application.service.SafeOpsKernel
import com.apptolast.platform.automation.domain.model.SafeCommand
import com.apptolast.platform.automation.domain.model.Whitelist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class SafeOpsKernelTest {

    private val recordingExecutor = object : CommandExecutor {
        var lastCommand: SafeCommand? = null
        override fun execute(command: SafeCommand, timeout: Duration): ExecutionOutcome {
            lastCommand = command
            return ExecutionOutcome(command, 0, "ok", "", 12L)
        }
    }

    @Test
    fun `READ_ONLY whitelist accepts kubectl get pods`() {
        val kernel = SafeOpsKernel(Whitelist.READ_ONLY, recordingExecutor)
        val outcome = kernel.run(SafeCommand.KubectlRead("get", "pods", "n8n", null))
        outcome.exitCode shouldBe 0
        recordingExecutor.lastCommand shouldBe SafeCommand.KubectlRead("get", "pods", "n8n", null)
    }

    @Test
    fun `READ_ONLY whitelist rejects helm-rollback`() {
        val kernel = SafeOpsKernel(Whitelist.READ_ONLY, recordingExecutor)
        val ex = assertThrows<CommandRejectedException> {
            kernel.run(SafeCommand.HelmRollback("n8n-prod", "n8n", 11))
        }
        ex.reason shouldContain "not in whitelist"
        recordingExecutor.lastCommand shouldBe null
    }

    @Test
    fun `namespace filter restricts to allowed namespaces`() {
        val restricted = Whitelist(
            allowedKinds = setOf("kubectl-read"),
            allowedNamespaces = setOf("cluster-ops"),
        )
        val kernel = SafeOpsKernel(restricted, recordingExecutor)
        val ex = assertThrows<CommandRejectedException> {
            kernel.run(SafeCommand.KubectlRead("get", "pods", "kube-system", null))
        }
        ex.reason shouldContain "namespace 'kube-system' not in whitelist"
    }

    @Test
    fun `ALLOW_TRIGGERS_AND_ROLLBACK accepts helm-rollback`() {
        val kernel = SafeOpsKernel(Whitelist.ALLOW_TRIGGERS_AND_ROLLBACK, recordingExecutor)
        val outcome = kernel.run(SafeCommand.HelmRollback("n8n-prod", "n8n", 11))
        outcome.exitCode shouldBe 0
    }

    @Test
    fun `Whitelist requires non-empty allowedKinds`() {
        assertThrows<IllegalArgumentException> {
            Whitelist(allowedKinds = emptySet())
        }
    }
}
