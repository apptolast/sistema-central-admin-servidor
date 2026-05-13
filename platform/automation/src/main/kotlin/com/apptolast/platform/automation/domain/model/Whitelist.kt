package com.apptolast.platform.automation.domain.model

/**
 * Lista blanca de SafeCommand kinds + filtros adicionales por namespace.
 *
 * Cualquier comando debe pasar:
 *   1. `kind` está en `allowedKinds`
 *   2. Para comandos namespaced, el namespace está en `allowedNamespaces`
 *      (o `allowedNamespaces.isEmpty()` que significa "todos").
 *
 * Diseñada para configurarse desde YAML por entorno (dev = abierta,
 * prod = restringida).
 */
data class Whitelist(
    val allowedKinds: Set<String>,
    val allowedNamespaces: Set<String> = emptySet(),
) {
    init {
        require(allowedKinds.isNotEmpty()) { "Whitelist must allow at least one kind" }
    }

    fun accepts(command: SafeCommand): Decision {
        if (command.kind !in allowedKinds) {
            return Decision.Reject("command kind '${command.kind}' not in whitelist $allowedKinds")
        }
        val ns = command.namespaceOrNull()
        if (ns != null && allowedNamespaces.isNotEmpty() && ns !in allowedNamespaces) {
            return Decision.Reject("namespace '$ns' not in whitelist $allowedNamespaces")
        }
        return Decision.Accept
    }

    private fun SafeCommand.namespaceOrNull(): String? = when (this) {
        is SafeCommand.KubectlRead -> namespace
        is SafeCommand.HelmRead -> namespace
        is SafeCommand.HelmRollback -> namespace
        is SafeCommand.TriggerCronJob -> namespace
    }

    sealed interface Decision {
        data object Accept : Decision
        data class Reject(val reason: String) : Decision
    }

    companion object {
        /** Whitelist por defecto: read-only operations en cualquier namespace. */
        val READ_ONLY = Whitelist(
            allowedKinds = setOf("kubectl-read", "helm-read"),
        )

        /** Whitelist permisiva incluye triggers y rollback. NUNCA en producción sin confirmación humana. */
        val ALLOW_TRIGGERS_AND_ROLLBACK = Whitelist(
            allowedKinds = setOf("kubectl-read", "helm-read", "helm-rollback", "trigger-cronjob"),
        )
    }
}
