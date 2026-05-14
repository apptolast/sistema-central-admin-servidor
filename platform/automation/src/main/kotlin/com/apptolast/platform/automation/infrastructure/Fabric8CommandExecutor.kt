package com.apptolast.platform.automation.infrastructure

import com.apptolast.platform.automation.application.port.outbound.CommandExecutor
import com.apptolast.platform.automation.application.port.outbound.ExecutionOutcome
import com.apptolast.platform.automation.domain.model.SafeCommand
import io.fabric8.kubernetes.api.model.batch.v1.CronJob
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.system.measureTimeMillis

/**
 * Fabric8-based executor — el único camino REAL hasta el cluster.
 *
 * Convierte `SafeCommand` (ya validado por `SafeOpsKernel` + `Whitelist`)
 * en llamadas fabric8 idempotentes y serializa la respuesta como YAML.
 */
class Fabric8CommandExecutor(
    private val client: KubernetesClient,
    private val helm: HelmCliSandbox = HelmCliSandbox(),
) : CommandExecutor {

    private val log = LoggerFactory.getLogger(Fabric8CommandExecutor::class.java)

    override fun execute(command: SafeCommand, timeout: Duration): ExecutionOutcome {
        when (command) {
            is SafeCommand.HelmRead -> return helm.execute(command, command.toHelmArgs(), timeout)
            is SafeCommand.HelmRollback -> return helm.execute(command, command.toHelmArgs(), timeout)
            else -> Unit
        }

        var stdout = ""
        var stderr = ""
        var exitCode = 0
        val durMs = measureTimeMillis {
            try {
                stdout = when (command) {
                    is SafeCommand.KubectlRead -> executeKubectlRead(command)
                    is SafeCommand.TriggerCronJob -> executeTriggerCronJob(command)
                    is SafeCommand.HelmRead,
                    is SafeCommand.HelmRollback,
                    -> error("unreachable: helm commands return before fabric8 execution")
                }
            } catch (e: Exception) {
                exitCode = 1
                stderr = "${e::class.java.simpleName}: ${e.message}"
                log.error("executor failed kind={} err={}", command.kind, e.message, e)
            }
        }
        return ExecutionOutcome(command, exitCode, stdout, stderr, durMs)
    }

    private fun executeKubectlRead(cmd: SafeCommand.KubectlRead): String {
        return when (cmd.verb) {
            "get", "describe" -> listOrGet(cmd)
            "logs" -> readLogs(cmd)
            "top" -> "(top not implemented via fabric8 — use kubectl-top metrics-server adapter)"
            else -> error("unreachable: SafeCommand.KubectlRead.init validates verb")
        }
    }

    private fun listOrGet(cmd: SafeCommand.KubectlRead): String {
        val result: Any? = when (cmd.resource.lowercase()) {
            "pod", "pods", "po" -> resolvePods(cmd)
            "service", "services", "svc" -> resolveServices(cmd)
            "persistentvolumeclaim", "persistentvolumeclaims", "pvc" -> resolvePvcs(cmd)
            "ingress", "ingresses", "ing" -> resolveIngresses(cmd)
            "namespace", "namespaces", "ns" -> resolveNamespaces(cmd)
            "node", "nodes", "no" -> resolveNodes(cmd)
            else -> throw IllegalArgumentException("unsupported resource: ${cmd.resource}")
        }
        return Serialization.asYaml(result)
    }

    private fun resolvePods(cmd: SafeCommand.KubectlRead): Any =
        if (cmd.name != null) {
            require(cmd.namespace != null) { "name without namespace is not supported" }
            client.pods().inNamespace(cmd.namespace).withName(cmd.name).get() ?: error("pod not found")
        } else if (cmd.namespace != null) {
            client.pods().inNamespace(cmd.namespace).list().items
        } else {
            client.pods().inAnyNamespace().list().items
        }

    private fun resolveServices(cmd: SafeCommand.KubectlRead): Any =
        if (cmd.name != null) {
            require(cmd.namespace != null) { "name without namespace is not supported" }
            client.services().inNamespace(cmd.namespace).withName(cmd.name).get() ?: error("service not found")
        } else if (cmd.namespace != null) {
            client.services().inNamespace(cmd.namespace).list().items
        } else {
            client.services().inAnyNamespace().list().items
        }

    private fun resolvePvcs(cmd: SafeCommand.KubectlRead): Any =
        if (cmd.name != null) {
            require(cmd.namespace != null) { "name without namespace is not supported" }
            client.persistentVolumeClaims().inNamespace(cmd.namespace).withName(cmd.name).get()
                ?: error("pvc not found")
        } else if (cmd.namespace != null) {
            client.persistentVolumeClaims().inNamespace(cmd.namespace).list().items
        } else {
            client.persistentVolumeClaims().inAnyNamespace().list().items
        }

    private fun resolveIngresses(cmd: SafeCommand.KubectlRead): Any =
        if (cmd.name != null) {
            require(cmd.namespace != null) { "name without namespace is not supported" }
            client.network().v1().ingresses().inNamespace(cmd.namespace).withName(cmd.name).get()
                ?: error("ingress not found")
        } else if (cmd.namespace != null) {
            client.network().v1().ingresses().inNamespace(cmd.namespace).list().items
        } else {
            client.network().v1().ingresses().inAnyNamespace().list().items
        }

    private fun resolveNamespaces(cmd: SafeCommand.KubectlRead): Any {
        return if (cmd.name != null) {
            client.namespaces().withName(cmd.name).get() ?: error("namespace not found")
        } else {
            client.namespaces().list().items
        }
    }

    private fun resolveNodes(cmd: SafeCommand.KubectlRead): Any {
        return if (cmd.name != null) {
            client.nodes().withName(cmd.name).get() ?: error("node not found")
        } else {
            client.nodes().list().items
        }
    }

    private fun readLogs(cmd: SafeCommand.KubectlRead): String {
        require(cmd.resource.lowercase() in setOf("pod", "pods", "po")) {
            "logs verb only valid for pods; got ${cmd.resource}"
        }
        require(cmd.namespace != null && cmd.name != null) { "logs requires namespace + name" }
        return client.pods()
            .inNamespace(cmd.namespace)
            .withName(cmd.name)
            .tailingLines(200)
            .log
    }

    /**
     * Equivalent to `kubectl -n <ns> create job --from=cronjob/<name> <name>-<ts>`.
     * Idempotente: el nombre incluye timestamp para no colisionar.
     */
    private fun executeTriggerCronJob(cmd: SafeCommand.TriggerCronJob): String {
        val cronJob = client.batch().v1().cronjobs()
            .inNamespace(cmd.namespace)
            .withName(cmd.cronJobName)
            .get()
            ?: error("cronjob not found: ${cmd.namespace}/${cmd.cronJobName}")

        val created = client.batch().v1().jobs()
            .inNamespace(cmd.namespace)
            .resource(buildManualJobFromCronJob(cmd, cronJob))
            .create()

        return Serialization.asYaml(created)
    }

    private fun SafeCommand.HelmRead.toHelmArgs(): List<String> =
        when (verb) {
            "history" -> listOf("history", release, "-n", namespace)
            "status" -> listOf("status", release, "-n", namespace)
            "get" -> listOf("get", "all", release, "-n", namespace)
            "list" -> listOf("list", "-n", namespace, "--filter", "^$release$")
            else -> error("unreachable: SafeCommand.HelmRead.init validates verb")
        }

    private fun SafeCommand.HelmRollback.toHelmArgs(): List<String> =
        listOf("rollback", release, revision.toString(), "-n", namespace, "--wait", "--timeout", "5m")
}

internal fun buildManualJobFromCronJob(
    cmd: SafeCommand.TriggerCronJob,
    cronJob: CronJob,
    timestampSeconds: Long = System.currentTimeMillis() / 1000,
): Job {
    val jobName = "${cmd.cronJobName}-manual-$timestampSeconds".take(63)

    return JobBuilder()
        .withNewMetadata()
            .withName(jobName)
            .withNamespace(cmd.namespace)
            .addToLabels("triggered-by", "platform-automation")
            .addToLabels("source-cronjob", cmd.cronJobName)
        .endMetadata()
        .withSpec(cronJob.spec.jobTemplate.spec)
        .build()
}
