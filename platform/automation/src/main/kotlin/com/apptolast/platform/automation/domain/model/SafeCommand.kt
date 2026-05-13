package com.apptolast.platform.automation.domain.model

/**
 * Command que el automation engine puede ejecutar.
 *
 * Modelado como sealed hierarchy para que el Whitelist haga matching exhaustivo
 * y rechace cualquier comando que no esté explícitamente permitido. Bash crudo
 * NO existe como tipo — solo formas tipadas y verificables.
 */
sealed interface SafeCommand {
    /** Identificador estable para auditoría y matching contra el whitelist. */
    val kind: String

    /** `kubectl get|describe|logs` (verbos read-only). */
    data class KubectlRead(
        val verb: String,
        val resource: String,
        val namespace: String?,
        val name: String?,
    ) : SafeCommand {
        override val kind: String = "kubectl-read"

        init {
            require(verb in ALLOWED_VERBS) { "kubectl-read verb must be one of $ALLOWED_VERBS; got $verb" }
            require(resource.matches(SAFE_TOKEN)) { "resource must be safe identifier; got $resource" }
            require(namespace == null || namespace.matches(SAFE_TOKEN)) { "namespace unsafe: $namespace" }
            require(name == null || name.matches(SAFE_TOKEN)) { "name unsafe: $name" }
        }

        companion object {
            val ALLOWED_VERBS = setOf("get", "describe", "logs", "top")
        }
    }

    /** `helm history|status|list` (read-only) — NO upgrade/rollback. */
    data class HelmRead(
        val verb: String,
        val release: String,
        val namespace: String,
    ) : SafeCommand {
        override val kind: String = "helm-read"

        init {
            require(verb in ALLOWED_VERBS) { "helm-read verb must be one of $ALLOWED_VERBS; got $verb" }
            require(release.matches(SAFE_TOKEN)) { "release unsafe: $release" }
            require(namespace.matches(SAFE_TOKEN)) { "namespace unsafe: $namespace" }
        }

        companion object {
            val ALLOWED_VERBS = setOf("history", "status", "list", "get")
        }
    }

    /**
     * Helm rollback a una revisión específica (destructivo).
     * Requiere HUMAN_CONFIRM mode + audit log obligatorio.
     */
    data class HelmRollback(
        val release: String,
        val namespace: String,
        val revision: Int,
    ) : SafeCommand {
        override val kind: String = "helm-rollback"

        init {
            require(release.matches(SAFE_TOKEN)) { "release unsafe: $release" }
            require(namespace.matches(SAFE_TOKEN)) { "namespace unsafe: $namespace" }
            require(revision > 0) { "revision must be > 0; got $revision" }
        }
    }

    /**
     * Disparar un CronJob ya existente (sin crear nuevo). Mapea a:
     * `kubectl -n <ns> create job --from=cronjob/<name> <name>-<timestamp>`
     */
    data class TriggerCronJob(
        val namespace: String,
        val cronJobName: String,
    ) : SafeCommand {
        override val kind: String = "trigger-cronjob"

        init {
            require(namespace.matches(SAFE_TOKEN)) { "namespace unsafe: $namespace" }
            require(cronJobName.matches(SAFE_TOKEN)) { "cronJobName unsafe: $cronJobName" }
        }
    }

    companion object {
        /**
         * Token RFC 1123-ish: letras, números, guión, punto. Sin espacios, sin
         * comodines, sin metacaracteres de shell. Cualquier comando que necesite
         * esos caracteres se rechaza por construcción.
         */
        private val SAFE_TOKEN = Regex("^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$", RegexOption.IGNORE_CASE)
    }
}
