package com.apptolast.platform.automation.domain

import com.apptolast.platform.automation.domain.model.AuditEntry
import com.apptolast.platform.automation.domain.model.AuditOutcome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class AuditEntryTest {

    private val now = Instant.parse("2026-05-13T18:00:00Z")

    @Test
    fun `AcceptedOk requires exit code 0`() {
        assertThrows<IllegalArgumentException> {
            AuditOutcome.AcceptedOk(exitCode = 1, durationMs = 0, stdoutExcerpt = "", stderrExcerpt = "")
        }
    }

    @Test
    fun `AcceptedFail requires exit code != 0`() {
        assertThrows<IllegalArgumentException> {
            AuditOutcome.AcceptedFail(exitCode = 0, durationMs = 0, stdoutExcerpt = "", stderrExcerpt = "")
        }
    }

    @Test
    fun `Rejected requires non-blank reason`() {
        assertThrows<IllegalArgumentException> {
            AuditOutcome.Rejected(reason = "  ")
        }
    }

    @Test
    fun `excerpt truncates strings beyond EXCERPT_MAX`() {
        val long = "x".repeat(AuditOutcome.EXCERPT_MAX + 100)
        AuditOutcome.excerpt(long) shouldHaveLength AuditOutcome.EXCERPT_MAX
        AuditOutcome.excerpt("short") shouldBe "short"
    }

    @Test
    fun `success is true only for AcceptedOk`() {
        val ok = sample(AuditOutcome.AcceptedOk(0, 1, "", ""))
        val fail = sample(AuditOutcome.AcceptedFail(1, 1, "", ""))
        val rej = sample(AuditOutcome.Rejected("nope"))
        val timeout = sample(AuditOutcome.TimedOut(31_000, "partial"))

        ok.success shouldBe true
        fail.success shouldBe false
        rej.success shouldBe false
        timeout.success shouldBe false
    }

    @Test
    fun `commandKind must not be blank`() {
        assertThrows<IllegalArgumentException> {
            sample(AuditOutcome.AcceptedOk(0, 1, "", ""), commandKind = "")
        }
    }

    @Test
    fun `outcome labels round-trip via label field`() {
        AuditOutcome.AcceptedOk(0, 0, "", "").label shouldBe "ACCEPTED_OK"
        AuditOutcome.AcceptedFail(1, 0, "", "").label shouldBe "ACCEPTED_FAIL"
        AuditOutcome.Rejected("x").label shouldBe "REJECTED"
        AuditOutcome.TimedOut(0, "").label shouldBe "TIMED_OUT"
    }

    private fun sample(outcome: AuditOutcome, commandKind: String = "kubectl-read") = AuditEntry(
        executedAt = now,
        commandKind = commandKind,
        commandPayload = """{"kind":"$commandKind"}""",
        executorKind = "TestExecutor",
        outcome = outcome,
    )
}
