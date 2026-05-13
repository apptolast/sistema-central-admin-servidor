package com.apptolast.platform.automation.domain

import com.apptolast.platform.automation.domain.model.SafeCommand
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SafeCommandTest {

    @Test
    fun `KubectlRead accepts valid get pods`() {
        val cmd = SafeCommand.KubectlRead("get", "pods", "n8n", null)
        cmd.kind shouldBe "kubectl-read"
    }

    @Test
    fun `KubectlRead rejects shell metacharacters in name`() {
        val ex = assertThrows<IllegalArgumentException> {
            SafeCommand.KubectlRead("get", "pods", "n8n", "foo;rm -rf /")
        }
        ex.message!! shouldContain "name unsafe"
    }

    @Test
    fun `KubectlRead rejects unknown verb`() {
        val ex = assertThrows<IllegalArgumentException> {
            SafeCommand.KubectlRead("delete", "pods", "n8n", null)
        }
        ex.message!! shouldContain "verb must be one of"
    }

    @Test
    fun `KubectlRead rejects path traversal in namespace`() {
        assertThrows<IllegalArgumentException> {
            SafeCommand.KubectlRead("get", "pods", "../etc/passwd", null)
        }
    }

    @Test
    fun `HelmRollback requires positive revision`() {
        assertThrows<IllegalArgumentException> {
            SafeCommand.HelmRollback("n8n-prod", "n8n", 0)
        }
    }

    @Test
    fun `HelmRead rejects upgrade verb`() {
        assertThrows<IllegalArgumentException> {
            SafeCommand.HelmRead("upgrade", "n8n-prod", "n8n")
        }
    }

    @Test
    fun `TriggerCronJob accepts cluster-ops names`() {
        val cmd = SafeCommand.TriggerCronJob("cluster-ops", "host-checks")
        cmd.kind shouldBe "trigger-cronjob"
    }
}
