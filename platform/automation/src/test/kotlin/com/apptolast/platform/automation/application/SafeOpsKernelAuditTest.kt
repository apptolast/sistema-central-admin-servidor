package com.apptolast.platform.automation.application

import com.apptolast.platform.automation.application.port.outbound.AuditLogRepository
import com.apptolast.platform.automation.application.port.outbound.AuditQuery
import com.apptolast.platform.automation.application.port.outbound.CommandExecutor
import com.apptolast.platform.automation.application.port.outbound.ExecutionOutcome
import com.apptolast.platform.automation.application.service.CommandRejectedException
import com.apptolast.platform.automation.application.service.SafeOpsKernel
import com.apptolast.platform.automation.domain.model.AuditEntry
import com.apptolast.platform.automation.domain.model.AuditOutcome
import com.apptolast.platform.automation.domain.model.SafeCommand
import com.apptolast.platform.automation.domain.model.Whitelist
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tests del wire SafeOpsKernel ↔ AuditLogRepository (Wave-E E2).
 *
 * Cubre el contrato: "TODA invocación a run() persiste exactamente UNA entry,
 * sin importar el outcome (aceptado, rechazado, fallo, timeout)".
 */
class SafeOpsKernelAuditTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-05-13T18:00:00Z"), ZoneOffset.UTC)
    private val audit = InMemoryAuditLog()

    @Test
    fun `accepted ok command persists ACCEPTED_OK entry`() {
        val executor = okExecutor("namespaces listed", "")
        val kernel = SafeOpsKernel(Whitelist.READ_ONLY, executor, audit, fixedClock)

        kernel.run(SafeCommand.KubectlRead("get", "namespaces", null, null))

        audit.all() shouldHaveSize 1
        val entry = audit.all()[0]
        entry.commandKind shouldBe "kubectl-read"
        entry.executedAt shouldBe Instant.parse("2026-05-13T18:00:00Z")
        entry.outcome.shouldBeInstanceOf<AuditOutcome.AcceptedOk>().let {
            it.exitCode shouldBe 0
            it.stdoutExcerpt shouldBe "namespaces listed"
        }
        entry.success shouldBe true
    }

    @Test
    fun `accepted but failing command persists ACCEPTED_FAIL entry`() {
        val executor = failingExecutor(exitCode = 1, stderr = "container not found")
        val kernel = SafeOpsKernel(Whitelist.READ_ONLY, executor, audit, fixedClock)

        kernel.run(SafeCommand.KubectlRead("get", "pods", "n8n", "doesnotexist"))

        audit.all() shouldHaveSize 1
        audit.all()[0].outcome.shouldBeInstanceOf<AuditOutcome.AcceptedFail>().let {
            it.exitCode shouldBe 1
            it.stderrExcerpt shouldBe "container not found"
        }
    }

    @Test
    fun `rejected command persists REJECTED entry and does NOT call executor`() {
        val recordingExecutor = TrackingExecutor()
        val kernel = SafeOpsKernel(Whitelist.READ_ONLY, recordingExecutor, audit, fixedClock)

        assertThrows<CommandRejectedException> {
            kernel.run(SafeCommand.HelmRollback("n8n-prod", "n8n", 11))
        }

        audit.all() shouldHaveSize 1
        audit.all()[0].outcome.shouldBeInstanceOf<AuditOutcome.Rejected>().let {
            it.reason.contains("not in whitelist") shouldBe true
        }
        // Sin executor invocation: REJECT corta antes.
        recordingExecutor.calls shouldBe 0
    }

    @Test
    fun `executor exception in audit save does NOT prevent the command response`() {
        val brokenAudit = object : AuditLogRepository {
            override fun save(entry: AuditEntry) = error("DB down")
            override fun findById(id: UUID): AuditEntry? = null
            override fun query(query: AuditQuery): List<AuditEntry> = emptyList()
        }
        val executor = okExecutor("ok", "")
        val kernel = SafeOpsKernel(Whitelist.READ_ONLY, executor, brokenAudit, fixedClock)

        // Command sigue funcionando aunque audit explote. Esto es deliberado:
        // un fallo en el log NO debe tumbar la operación que se está auditando.
        // Operativamente, esto debería disparar alerta (Phase 6 hardening).
        val outcome = kernel.run(SafeCommand.KubectlRead("get", "pods", "n8n", null))
        outcome.success shouldBe true
    }

    @Test
    fun `kernel without audit repo (null) does not throw`() {
        // Compatibilidad con SafeOpsKernelTest legado: audit es opcional.
        val executor = okExecutor("ok", "")
        val kernel = SafeOpsKernel(Whitelist.READ_ONLY, executor, audit = null, clock = fixedClock)
        val outcome = kernel.run(SafeCommand.KubectlRead("get", "pods", "n8n", null))
        outcome.success shouldBe true
        // audit del test sigue vacío (no es el inyectado).
        audit.all().shouldBeEmpty()
    }

    @Test
    fun `multiple invocations persist independent entries (no de-dup)`() {
        val executor = okExecutor("ok", "")
        val kernel = SafeOpsKernel(Whitelist.READ_ONLY, executor, audit, fixedClock)

        kernel.run(SafeCommand.KubectlRead("get", "pods", "n8n", null))
        kernel.run(SafeCommand.KubectlRead("get", "services", "n8n", null))
        kernel.run(SafeCommand.HelmRead("history", "n8n-prod", "n8n"))

        audit.all() shouldHaveSize 3
        audit.all().map { it.commandKind } shouldBe listOf("kubectl-read", "kubectl-read", "helm-read")
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun okExecutor(stdout: String, stderr: String) = object : CommandExecutor {
        override fun execute(command: SafeCommand, timeout: Duration): ExecutionOutcome =
            ExecutionOutcome(command, 0, stdout, stderr, 42L)
    }

    private fun failingExecutor(exitCode: Int, stderr: String) = object : CommandExecutor {
        override fun execute(command: SafeCommand, timeout: Duration): ExecutionOutcome =
            ExecutionOutcome(command, exitCode, "", stderr, 17L)
    }

    private class TrackingExecutor : CommandExecutor {
        var calls = 0
        override fun execute(command: SafeCommand, timeout: Duration): ExecutionOutcome {
            calls++
            return ExecutionOutcome(command, 0, "", "", 0L)
        }
    }

    /** Adapter in-memory para tests; mantiene un List<AuditEntry> ordenado por inserción. */
    private class InMemoryAuditLog : AuditLogRepository {
        private val store = mutableListOf<AuditEntry>()
        override fun save(entry: AuditEntry) {
            store.add(entry)
        }
        override fun findById(id: UUID): AuditEntry? = store.firstOrNull { it.id == id }
        override fun query(query: AuditQuery): List<AuditEntry> = store.toList()
        fun all(): List<AuditEntry> = store.toList()
    }
}
