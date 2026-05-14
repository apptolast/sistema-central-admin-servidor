package com.apptolast.platform.automation.infrastructure

import com.apptolast.platform.automation.domain.model.SafeCommand
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class HelmCliSandboxTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `executes helm through absolute binary and preserves argument boundaries`() {
        val helm = fakeHelm(
            """
            #!/usr/bin/env bash
            printf '<%s>\n' "$@"
            """.trimIndent(),
        )
        val sandbox = HelmCliSandbox(candidatePaths = listOf(helm))

        val outcome = sandbox.execute(
            SafeCommand.HelmRead("status", "n8n-prod", "n8n"),
            listOf("status", "n8n-prod", "-n", "n8n"),
            Duration.ofSeconds(2),
        )

        outcome.exitCode shouldBe 0
        outcome.stdout shouldContain "<status>"
        outcome.stdout shouldContain "<n8n-prod>"
        outcome.stdout shouldContain "<-n>"
        outcome.stdout shouldContain "<n8n>"
    }

    @Test
    fun `does not interpret metacharacters as shell syntax`() {
        val marker = tempDir.resolve("pwned")
        val helm = fakeHelm(
            """
            #!/usr/bin/env bash
            printf '<%s>\n' "$@"
            """.trimIndent(),
        )
        val sandbox = HelmCliSandbox(candidatePaths = listOf(helm))

        val outcome = sandbox.execute(
            SafeCommand.HelmRead("status", "n8n-prod", "n8n"),
            listOf("status", "release;touch ${marker.toAbsolutePath()}"),
            Duration.ofSeconds(2),
        )

        outcome.exitCode shouldBe 0
        outcome.stdout shouldContain "release;touch ${marker.toAbsolutePath()}"
        Files.exists(marker).shouldBeFalse()
    }

    @Test
    fun `kills helm process on timeout`() {
        val helm = fakeHelm(
            """
            #!/usr/bin/env bash
            sleep 10
            """.trimIndent(),
        )
        val sandbox = HelmCliSandbox(candidatePaths = listOf(helm))

        val outcome = sandbox.execute(
            SafeCommand.HelmRead("status", "n8n-prod", "n8n"),
            listOf("status", "n8n-prod"),
            Duration.ofMillis(100),
        )

        outcome.exitCode shouldBe 124
        outcome.stderr shouldContain "timed out"
    }

    @Test
    fun `limits stdout buffer`() {
        val helm = fakeHelm(
            """
            #!/usr/bin/env bash
            yes x | head -c 2048
            """.trimIndent(),
        )
        val sandbox = HelmCliSandbox(candidatePaths = listOf(helm), maxBufferBytes = 64)

        val outcome = sandbox.execute(
            SafeCommand.HelmRead("status", "n8n-prod", "n8n"),
            listOf("status", "n8n-prod"),
            Duration.ofSeconds(2),
        )

        outcome.exitCode shouldBe 0
        outcome.stdout.length shouldBeGreaterThan 64
        outcome.stdout.length shouldBeLessThanOrEqual 80
        outcome.stdout shouldContain "[truncated]"
    }

    @Test
    fun `returns 127 when helm is outside allowed paths`() {
        val sandbox = HelmCliSandbox(candidatePaths = listOf(tempDir.resolve("missing-helm")))

        val outcome = sandbox.execute(
            SafeCommand.HelmRead("status", "n8n-prod", "n8n"),
            listOf("status", "n8n-prod"),
            Duration.ofSeconds(2),
        )

        outcome.exitCode shouldBe 127
        outcome.stderr shouldContain "helm binary not found"
    }

    private fun fakeHelm(script: String): Path {
        val helm = tempDir.resolve("helm")
        Files.writeString(helm, script)
        helm.toFile().setExecutable(true)
        return helm
    }
}
