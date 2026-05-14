package com.apptolast.platform.automation.infrastructure

import com.apptolast.platform.automation.domain.model.SafeCommand
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@EnableKubernetesMockClient(crud = true)
class Fabric8CommandExecutorTest {

    lateinit var client: KubernetesClient
    private lateinit var executor: Fabric8CommandExecutor

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        executor = Fabric8CommandExecutor(client)
    }

    @Test
    fun `kubectl get pods lists all in namespace`() {
        listOf("a", "b", "c").forEach { name ->
            client.pods().inNamespace("cluster-ops").resource(
                PodBuilder()
                    .withNewMetadata().withName(name).withNamespace("cluster-ops").endMetadata()
                    .build(),
            ).create()
        }

        val outcome = executor.execute(
            SafeCommand.KubectlRead("get", "pods", "cluster-ops", null),
        )

        outcome.success shouldBe true
        outcome.stdout shouldContain "name: \"a\""
        outcome.stdout shouldContain "name: \"b\""
        outcome.stdout shouldContain "name: \"c\""
    }

    @Test
    fun `kubectl get unsupported resource returns exit 1 with stderr`() {
        val outcome = executor.execute(
            SafeCommand.KubectlRead("get", "deployments", "n8n", null),
        )
        outcome.exitCode shouldBe 1
        outcome.stderr shouldContain "unsupported resource"
    }

    @Test
    fun `HelmRead delegates to sandbox with safe args`() {
        val helm = fakeHelm()
        val helmExecutor = Fabric8CommandExecutor(client, HelmCliSandbox(candidatePaths = listOf(helm)))

        val outcome = helmExecutor.execute(SafeCommand.HelmRead("status", "n8n-prod", "n8n"))

        outcome.exitCode shouldBe 0
        outcome.stdout shouldContain "<status>"
        outcome.stdout shouldContain "<n8n-prod>"
        outcome.stdout shouldContain "<-n>"
        outcome.stdout shouldContain "<n8n>"
    }

    @Test
    fun `HelmRollback delegates to sandbox with wait and timeout`() {
        val helm = fakeHelm()
        val helmExecutor = Fabric8CommandExecutor(client, HelmCliSandbox(candidatePaths = listOf(helm)))

        val outcome = helmExecutor.execute(SafeCommand.HelmRollback("n8n-prod", "n8n", 11))

        outcome.exitCode shouldBe 0
        outcome.stdout shouldContain "<rollback>"
        outcome.stdout shouldContain "<n8n-prod>"
        outcome.stdout shouldContain "<11>"
        outcome.stdout shouldContain "<--wait>"
        outcome.stdout shouldContain "<--timeout>"
        outcome.stdout shouldContain "<5m>"
    }

    @Test
    fun `TriggerCronJob builds a manual Job from the CronJob spec`() {
        val cron = CronJobBuilder()
            .withNewMetadata().withName("host-checks").withNamespace("cluster-ops").endMetadata()
            .withNewSpec()
                .withSchedule("*/5 * * * *")
                .withNewJobTemplate()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .withRestartPolicy("OnFailure")
                                .addNewContainer()
                                    .withName("checker")
                                    .withImage("busybox:latest")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endJobTemplate()
            .endSpec()
            .build()

        val job = buildManualJobFromCronJob(
            SafeCommand.TriggerCronJob("cluster-ops", "host-checks"),
            cron,
            timestampSeconds = 1_776_000_000,
        )

        job.metadata.name shouldBe "host-checks-manual-1776000000"
        job.metadata.namespace shouldBe "cluster-ops"
        job.metadata.labels["triggered-by"] shouldBe "platform-automation"
        job.metadata.labels["source-cronjob"] shouldBe "host-checks"
        job.spec.template.spec.containers[0].image shouldBe "busybox:latest"
    }

    @Test
    fun `TriggerCronJob fails clearly when CronJob does not exist`() {
        val outcome = executor.execute(
            SafeCommand.TriggerCronJob("cluster-ops", "nonexistent"),
        )
        outcome.exitCode shouldBe 1
        outcome.stderr shouldContain "cronjob not found"
    }

    private fun fakeHelm(): Path {
        val helm = tempDir.resolve("helm")
        Files.writeString(
            helm,
            """
            #!/usr/bin/env bash
            printf '<%s>\n' "$@"
            """.trimIndent(),
        )
        helm.toFile().setExecutable(true)
        return helm
    }
}
