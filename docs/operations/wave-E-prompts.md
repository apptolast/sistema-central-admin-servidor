---
title: "Wave E — Phase 5 Automation hardening prompts"
type: policy
owner: pablo
last-verified: 2026-05-13
audience: [phase-orchestrator-routine, oncall]
applies-to-wave: E (Phase 5 — Automation completion)
precondition:
  - wave-D-merged (identity + secrets)
  - SafeOps kernel + Fabric8CommandExecutor landed (this commit)
  - AutomationController REST landed (this commit)
---

# Wave E — Phase 5 Automation prompts

## E1 — Helm CLI sandbox (cierra HelmRead/HelmRollback TODO)

**Prompt al backend-dev (owner: platform/automation):**

```
Fabric8CommandExecutor lanza UnsupportedOperationException para HelmRead y
HelmRollback. Implementa un sandbox seguro usando ProcessBuilder con list-args
(NO shell interpolation):

1. infrastructure/HelmCliSandbox.kt:
   - Resuelve binario en PATH (helm) con whitelist absoluta de paths
     (/usr/local/bin/helm, /usr/bin/helm)
   - Ejecuta con ProcessBuilder(listOf("helm", verb, release, "-n", namespace, ...))
     — NUNCA String.split(" ") (esa es la puerta a injection)
   - Timeout configurable, default 30s
   - Captura stdout/stderr en buffers limitados (1MB max — evita OOM)
   - Si exitcode != 0, devuelve ExecutionOutcome con stderr truncado

2. Wire en Fabric8CommandExecutor:
   - HelmRead → sandbox.exec(listOf("helm", cmd.verb, cmd.release, "-n", cmd.namespace))
   - HelmRollback → sandbox.exec(listOf("helm", "rollback", cmd.release,
     cmd.revision.toString(), "-n", cmd.namespace, "--wait", "--timeout", "5m"))

3. Tests:
   - HelmCliSandboxTest con helm binario simulado (script bash que echo args)
   - Verificar que argumentos con metacharacters NO se interpretan como shell
   - Verificar timeout kill funciona
   - Verificar buffer overflow protection

OWNERSHIP: platform/automation/src/main/kotlin/.../infrastructure/.
NO toques: SafeOpsKernel, Whitelist, SafeCommand, controller.

VERIFICACIÓN: re-habilitar los 2 tests @Disabled en Fabric8CommandExecutorTest
no aplica — esos eran de KubernetesMockServer, no de Helm. Pero los nuevos
tests de HelmCliSandbox deben pasar.
```

## E2 — Audit log de comandos automation

**Prompt al backend-dev:**

```
Cada SafeOpsKernel.run() debe escribir un audit log persistente. Diseño:

1. domain/model/AuditEntry.kt — data class con commandKind, requestedBy,
   acceptedOrRejected, reasonIfRejected, executionDurationMs, executedAtUtc,
   exitCode, stderrTail (max 500 chars).

2. application/port/outbound/AuditLog.kt — puerto con append(entry).

3. infrastructure/JpaAuditLogAdapter.kt — JPA entity + repository, persiste
   en tabla automation_audit_log. Flyway migration V1__automation_audit_log.sql:
   id BIGSERIAL, command_kind TEXT, requested_by TEXT, accepted BOOL,
   reason TEXT NULL, duration_ms BIGINT, executed_at_utc TIMESTAMP WITH TIME ZONE,
   exit_code INT, stderr_tail TEXT NULL. Index en (requested_by, executed_at_utc DESC).

4. Modificar SafeOpsKernel.run() para llamar AuditLog.append() siempre
   (accepted o rejected). NO debe lanzar excepción si audit falla (degradation
   graceful — el audit es complementario, no crítico).

5. Tests:
   - In-memory AuditLog stub
   - Verifica que kernel registra entry incluso cuando se rechaza
   - Verifica que stderr se trunca a 500 chars

OWNERSHIP: platform/automation/**.
```

## E3 — Automation REST: endpoint /api/v1/automation/audit?since=...&kind=...

**Prompt al backend-dev:**

```
Extiende AutomationController con GET /api/v1/automation/audit:

- Query params: since (ISO8601, default now-24h), kind (optional filter),
  limit (default 100, max 1000)
- Devuelve List<AuditEntry> ordenado executed_at_utc DESC
- Requiere ROLE_ADMIN o ROLE_ONCALL (cuando wave-D Spring Security esté
  desplegado). De momento sin restricción — añadir TODO comment.

Tests directos (sin MockMvc, mismo patrón documentado).
```

## E4 — Helm chart de platform-app (consolidado)

**Prompt al devops-engineer:**

```
k8s/helm/platform/ ya existe pero le falta:

1. Wire OPENAI_API_KEY via Secret (passbolt-managed) — añadir a env del
   container. Default empty si secret no existe.

2. Wire JWT issuer-uri vía ConfigMap pointing a keycloak.apptolast.com cuando
   wave-D esté desplegado.

3. NetworkPolicy: solo cluster-watcher → platform-app /api/v1/internal/* allow.
   Lo demás de platform-app sólo expuesto vía IngressRoute Traefik.

4. PodDisruptionBudget minAvailable=1 (no útil en single-node pero documenta intent).

5. helm lint + kubectl apply --dry-run=client.
```

## Verificación end-of-wave-E

```bash
cd platform && ./gradlew :automation:test
helm lint k8s/helm/platform/ k8s/helm/keycloak/

# Smoke test contra cluster real (cuando desplegado):
curl -fsS https://automation.apptolast.com/api/v1/automation/run \
  -X POST -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT" \
  -d '{"kind":"kubectl-read","verb":"get","resource":"pods","namespace":"cluster-ops"}'
```

## Citación

- SafeOps kernel: platform/automation/src/main/kotlin/.../service/SafeOpsKernel.kt
- Whitelist presets: platform/automation/src/main/kotlin/.../domain/model/Whitelist.kt
- Anti-shell-injection design: AutomationController kdoc + SafeCommand.SAFE_TOKEN
