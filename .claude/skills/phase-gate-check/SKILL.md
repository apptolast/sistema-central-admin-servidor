---
name: phase-gate-check
description: >
  Ejecuta el conjunto completo de quality gates de una Fase del roadmap antes de declararla completa.
  Verifica: build verde, tests verdes, lint, ArchUnit + Modulith, criterios de aceptación específicos
  de la Fase (de ARCHITECTURE.md §4), docs frontmatter, ADRs presentes si aplican. User-invocable a
  demanda con /phase-kickoff <N> --gate o como pre-merge gate en el team-lead.
---

# Skill: phase-gate-check

Quality gate exhaustivo antes de declarar una Fase del roadmap completa.

Ver `ARCHITECTURE.md` §4 (roadmap por fases) para los criterios de salida específicos de cada Fase.

## Cuándo usar

- El team-lead la invoca antes de declarar una Fase done y mergear el PR final de la wave
- A demanda con `/phase-kickoff <N> --gate` para auditar el estado actual
- En `routines/phase-progress-report` para reportar cuánto falta para cerrar la Fase activa

## Estructura del check

Cada Fase tiene un **criterio de salida** definido en `ARCHITECTURE.md` §4. Este skill convierte cada criterio en un check ejecutable.

### Fase 0 — Bootstrap

```bash
# Criterio: repo + CI verde + Spring Boot mínimo levantando con /health 200
- [ ] git ls-files | grep -qE "(CLAUDE|ARCHITECTURE|README)\.md"
- [ ] git ls-files | grep -qE "\.github/workflows/ci\.yml"
- [ ] gh run list --workflow=ci.yml --branch=main --limit=1 --json conclusion -q '.[0].conclusion' = "success"
- [ ] test -f platform/platform-app/src/main/kotlin/com/apptolast/platform/PlatformApplication.kt
- [ ] cd platform && ./gradlew :platform-app:build
- [ ] # /health 200 — opcional, requiere boot local
```

### Fase 1 — Inventory + cluster-watcher + UI esqueleto

```bash
# Criterio: deployable a dev. inventory.apptolast.com muestra 126 pods + 80 DNS + 30 PVCs
- [ ] test -d platform/inventory/{domain,application,infrastructure,api}
- [ ] git ls-files platform/inventory | grep -qE "package-info\.java"  # @ApplicationModule presente
- [ ] git ls-files platform/inventory/src/main/resources/db/migration | grep -qE "V[0-9]+__inventory.*\.sql"
- [ ] cd platform && ./gradlew :inventory:test --tests "*ArchitectureTest*"
- [ ] test -d services/cluster-watcher
- [ ] test -d frontend/composeApp/src/commonMain/kotlin/com/apptolast/platform/ui/screens/inventory
- [ ] grep -q "PodObserved" platform/inventory/api/events/  # Domain event existe
- [ ] # cd platform && ./gradlew :platform-app:bootRun &  # opcional: arrancar y curl /health
- [ ] # curl -fsS http://localhost:8080/api/v1/inventory/pods | jq length  # >= 1
- [ ] gh pr list --search "Phase 1" --state merged --json number | jq length >= 1
```

### Fase 2 — Observability stack

```bash
# Criterio: alerta de Kuma cae → IDP → Discord → histórico. 17 dolores P0/P1 monitorizados
- [ ] test -f docs/adrs/0005-nats-vs-inmemory-bus.md
- [ ] test -f docs/adrs/0006-otel-collector-shape.md
- [ ] test -d k8s/helm/otel-collector
- [ ] test -d platform/observability
- [ ] grep -q "@ApplicationModule" platform/observability/src/main/java/**/package-info.java
- [ ] helm lint k8s/helm/otel-collector
- [ ] # Verificación operativa requiere despliegue real
```

### Fase 3 — Segundo cerebro RAG

```bash
- [ ] test -f docs/adrs/0007-llm-provider-embeddings.md
- [ ] test -d services/rag-ingestor
- [ ] test -d services/rag-query/{python-r2r,kotlin-facade}
- [ ] test -d platform/knowledge
- [ ] # Tests E2E del RAG: /ask "¿qué Postgres hay desplegados?" debe retornar lista con citas resolvibles
- [ ] # /ask "¿qué es el módulo lunar?" → "no encuentro evidencia documentada"
- [ ] find docs/runbooks -name "*.md" | wc -l >= 27  # los 27 migrados desde cluster-ops/audit/RUNBOOKS/
```

### Fase 4 — Identity + secrets

```bash
- [ ] kubectl -n identity get pod | grep -q keycloak
- [ ] test -d platform/identity
- [ ] test -d platform/secrets
- [ ] # Login OIDC funciona end-to-end (manual)
- [ ] # Password de n8n migrado a Secret (no env plaintext) — dolor P0 #10 del dossier resuelto
```

### Fase 5 — Automation

```bash
- [ ] test -d platform/automation
- [ ] # UI Automation muestra los ~30 cronjobs (18 cluster-ops + 5 n8n + 3 host crontab + cronjobs de apps)
- [ ] # Trigger manual funciona con auditoría en histórico
```

### Fase 6 — Hardening (ataca dolores P0/P1 del dossier)

```bash
# Lista de dolores P0/P1 del dossier 2026-05-12 que deben quedar resueltos:
- [ ] # 1. Backup off-site Longhorn → Hetzner Storage Box configurado
- [ ] kubectl -n longhorn-system get setting backup-target -o jsonpath='{.value}' | grep -qE '^s3://'
- [ ] # 2. Cronjobs pg_dump para los 9 Postgres + MySQL gibbon
- [ ] kubectl get cronjobs -A | grep -c "pg-backup\|mysql-backup" >= 10
- [ ] # 3. Certs huérfanos eliminados (prometheus.*, grafana.*, alertmanager.*, kali.*, generator-ui.*, llm-router.*, rag-service.*)
- [ ] kubectl get certificates -A | grep -cE "(prometheus|grafana|alertmanager|kali|generator-ui|llm-router|rag-service)-.*-tls" = 0
- [ ] # 4. Helm n8n-prod failed → rollback a deployed
- [ ] helm status n8n-prod -n n8n -o json | jq -r '.info.status' = "deployed"
- [ ] # 5. containerd movido a HC Volume → /var/lib/containerd < 5G
- [ ] # 6. Password n8n migrado a Secret
- [ ] kubectl -n n8n get deployment n8n-prod -o yaml | grep -A2 POSTGRES_PASSWORD | grep -q valueFrom
- [ ] # 7. NodePorts de DBs bloqueados (TimescaleDB 30432, PG metadata 30433, PG whoop 30434, Redis 30379, EMQX 30880s)
- [ ] # Verificación: hcloud firewall describe o NetworkPolicy en cada ns
```

### Fase 7 — Topology + agentic + design system

```bash
- [ ] test -d services/rag-query/cognee  # Si Cognee se adopta
- [ ] test -f frontend/composeApp/src/commonMain/kotlin/com/apptolast/platform/ui/components/charts/TopologyGraph.kt
- [ ] # Open WebUI integrado y respondiendo
- [ ] # Design system exportado de Claude Design a docs/design/tokens.json o similar
```

## Cómo invocar

```bash
# Auditar la fase activa según ARCHITECTURE.md
.claude/skills/phase-gate-check/check.sh

# Auditar una fase específica
.claude/skills/phase-gate-check/check.sh --phase 1

# Auditar todas las fases (reporte tipo dashboard)
.claude/skills/phase-gate-check/check.sh --all
```

## Output

```
═══════════════════════════════════════════════════════════════
Phase 1 — Inventory + cluster-watcher + UI esqueleto
═══════════════════════════════════════════════════════════════
✓ platform/inventory/ existe con estructura hexagonal
✓ Flyway migration V1__inventory_init.sql presente
✓ ArchitectureTest verde
✓ services/cluster-watcher/ existe
✓ Frontend PodsList screen presente
✓ Domain event PodObserved definido
✗ PR "Phase 1" no encontrado como merged
─────────────────────────────────────────────────────────────
Resumen: 6/7 checks · STATUS: NEAR-COMPLETE (falta merge PR final)
```

Exit codes:
- `0`: todas las checks de la fase OK → Fase puede declararse done
- `1`: al menos un check falla → Fase NO ready, ver detalle

## Reglas

- **Cero alucinación**: cada check verifica un hecho ejecutable (file exists, command exit code, JSON jq query). Sin checks subjetivos.
- **Cite los criterios**: cuando un check falla, el mensaje incluye link a `ARCHITECTURE.md` §4 fila de la Fase para que el usuario entienda por qué importa.
- **No bloquees por flaky**: si un check requiere infraestructura externa (kubectl access, gh API), capturar timeout y reportar como `SKIPPED` no `FAILED`.
