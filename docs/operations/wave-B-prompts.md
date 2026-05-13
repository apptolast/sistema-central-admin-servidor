---
title: "Wave B — Prompts copy-paste para Fase 2 arranque (NATS + observability skeleton)"
type: runbook
owner: pablo
source-of-truth: "ls /home/admin/sistema-central-admin-servidor/.claude/agents/ && cat /home/admin/sistema-central-admin-servidor/.claude/rules/modulith-rules.md"
last-verified: 2026-05-13
status: stable
phase: 2
tags:
  - runbook
  - agent-teams
  - phase-2
  - wave-b
  - nats
  - observability
depends-on:
  - repo:apptolast/sistema-central-admin-servidor
  - namespace:apptolast-platform-dev
  - wave:A
related-docs:
  - docs/operations/wave-A-prompts.md
  - docs/marathon-plan.md
  - .claude/rules/ownership.md
  - .claude/rules/modulith-rules.md
  - .claude/rules/citation-policy.md
  - .claude/agents/team-lead.md
  - docs/adrs/0001-spring-modulith-vs-microservices.md
  - docs/adrs/0005-nats-jetstream-event-bus.md
superseded-by: null
---

# Wave B — Fase 2 spawn prompts (arranque)

Wave B abre la Fase 2: despliega NATS JetStream para cerrar el TODO de Wave A (in-memory bus → NATS real), introduce el OTEL Collector y el stack de observabilidad (VictoriaMetrics + Loki + Grafana), y crea el esqueleto del módulo `observability` en el monolito. Es una wave más pequeña (4 teammates) y deliberadamente parcial: deja la implementación de adaptadores observability para Wave C.

Citation-first: cada decisión referencia archivo y sección. Si no existe, créalo o pregunta al lead.

---

## Cómo usar este documento

1. Pablo abre terminal en `/home/admin/sistema-central-admin-servidor/`.
2. Ejecuta `claude` (sesión interactiva v2.1.139+).
3. Inicia conversación con el **Prompt inicial al team-lead** (sección 1).
4. El lead invoca `TeamCreate` con nombre sugerido `fase2-observability-skeleton`.
5. El lead spawna cada teammate copiando el **Prompt de spawn** correspondiente (secciones 2-5).
6. Delegate Mode (`Shift+Tab`) opcional aquí — son 4 teammates, manageable sin delegate.

Orden de spawn (respeta `blockedBy`):

- t=0: `architect` (B1 y B2 en paralelo, mismo teammate hace los dos ADRs secuencialmente).
- t=B2-done: `devops-engineer` (B3).
- t=B1-done: `backend-dev` (B4).
- t=B1+B3-done: `tech-writer` (B5).

---

## 1. Prompt inicial al team-lead

```
Eres el team-lead del proyecto AppToLast IDP (repo apptolast/sistema-central-admin-servidor en /home/admin/sistema-central-admin-servidor/). Tu rol es coordinación pura. Lee `.claude/agents/team-lead.md` antes de actuar.

PREREQUISITO: la Wave A de Fase 1 debe estar merged en main. Verifica con `git log --oneline main..HEAD` y `gh pr list --state merged --limit 5` que ves el PR "Phase 1 — Inventory + cluster-watcher + UI esqueleto" cerrado.

Onboarding obligatorio ANTES de TeamCreate:
1. `CLAUDE.md` (convenciones, ownership)
2. `ARCHITECTURE.md` §3 (módulos, especialmente observability), §4 (roadmap Fase 2), §6 (presupuestos: NATS 200-400 MB, OTEL Collector 100-200 MB, VictoriaMetrics + Loki + Grafana ~2 GB combinados)
3. `.claude/rules/ownership.md`, `.claude/rules/modulith-rules.md` (allowedDependencies para observability = {"inventory"})
4. `docs/adrs/0005-nats-jetstream-event-bus.md` (escrito en Wave A — base para B1)
5. `docs/modules/inventory.md` (escrito en Wave A — base para entender la integración inventory ↔ observability)

Crea el team con TeamCreate nombre `fase2-observability-skeleton`. Componentes (4 teammates):
- architect (model: opus) — Tasks B1 y B2 (secuenciales, mismo teammate)
- backend-dev (sonnet) — Task B4
- devops-engineer (sonnet) — Task B3
- tech-writer (sonnet) — Task B5

Orden de spawn:
1) architect primero (B1 + B2).
2) Cuando B2 esté done → devops-engineer (B3).
3) Cuando B1 esté done → backend-dev (B4) en paralelo con devops.
4) Cuando B1 y B3 done → tech-writer (B5).

Crea las 5 tasks con TaskCreate batch antes de spawnar. Cada task: subject imperativo corto, description con archivos a tocar y comandos de verify, blockedBy con IDs reales.

Monitoreo: TaskList cada 30 min. SendMessage si > 30 min sin actualización.

Recordatorios críticos:
- ktlint y detekt siguen DEFERRED — los teammates NO los ejecutan.
- ModulithVerificationTest ya está activo (reactivado en Wave A) — backend-dev (B4) DEBE verificar que su skeleton no rompe la verify.
- NATS llega A CLUSTER en esta wave (B3). El TODO marcado en Wave A en `services/cluster-watcher/.../InMemoryClusterEventBus.kt` y en `platform/inventory/.../InMemoryInventoryEventBus.kt` se cierra en Wave C, NO en B. Wave B sólo despliega NATS y deja el adaptador NATS escrito como interface stub.
- Wave B es deliberadamente parcial — backend-dev SÓLO crea el SKELETON del módulo observability (api/ + application/ + package-info.java). Sin adaptadores reales. Esos llegan en Wave C cuando OTEL Collector esté emitiendo.

Quality gate antes de declarar wave done:
- `./gradlew :platform-app:test --tests "*Modulith*"` verde (allowedDependencies={"inventory"} validado).
- `helm lint k8s/helm/{nats,otel-collector,victoriametrics,loki,grafana}` verdes.
- ADR-0005 actualizado (status: accepted-deployed) y ADR-0006 escrito.

Cleanup al final (ver sección 6 de este runbook).
```

---

## 2. Spawn: architect — Task B1 (Opus)

**Task B1 — ADR-0005 actualización: NATS JetStream deployment + dimensionamiento**

```
WORKER PREAMBLE: Eres un agente WORKER architect del team `fase2-observability-skeleton`. NO spawnes otros agentes. Reporta al lead vía SendMessage. Tu ownership: `docs/adrs/**`, `docs/architecture.md`, `ARCHITECTURE.md`, `platform/<module>/api/**`. NO tocas código de producción ni tests.

Bloqueo: ninguno (puedes empezar inmediato).

Lee antes de empezar:
- `docs/adrs/0005-nats-jetstream-event-bus.md` (escrito en Wave A como `accepted-pending-deployment`).
- `ARCHITECTURE.md` §3 (event bus inter-módulo), §5 (patrones), §6 (presupuesto NATS 200-400 MB RAM).
- `.claude/rules/modulith-rules.md` §"Sobre eventos vs llamadas síncronas".
- `docs/modules/inventory.md` (consumidores reales del bus que llegan en Wave C).

Entregables (commit `docs(adr): nats jetstream deployment + dimensioning`):

1. **Actualizar `docs/adrs/0005-nats-jetstream-event-bus.md`**:
   - Cambia `status: accepted-pending-deployment` → `status: accepted` y añade campo `deployed-version: 2.20.5` en frontmatter.
   - Añade sección `## Plan de despliegue (Wave B)`:
     - Topología: single-node NATS con JetStream habilitado, PersistentVolume de 5 Gi en Longhorn para WAL.
     - Replicación: factor 1 en cluster single-node; documentar trade-off (HA llega cuando haya 3+ nodes).
     - Subjects: `inventory.<kind>.<verb>` (e.g., `inventory.pod.added`, `inventory.cert.expiring`), `observability.alert.<severity>`, `automation.job.<state>`. Wildcards documentados.
     - Streams: `INVENTORY` (retention 7d, max 100k messages), `OBSERVABILITY` (retention 30d, max 1M), `AUTOMATION` (retention 90d, max 50k).
     - Consumers: pull-based con ACK explícito; max-deliver 5 con DLQ subject `dlq.<original-subject>`.
   - Añade sección `## Dimensionamiento`:
     - RAM request 200 Mi, limit 400 Mi (ARCHITECTURE.md §6).
     - CPU request 100m, limit 500m.
     - Storage 5 Gi PVC (Longhorn, single-replica).
     - Conexiones esperadas: ~10 (monolito + cluster-watcher + rag-ingestor futuro).
   - Añade sección `## Migración del bus in-memory`:
     - Wave B: NATS desplegado pero NO conectado al monolito ni cluster-watcher.
     - Wave C: implementar `NatsInventoryEventBus` (adapter) y `NatsClusterEventPublisher`; feature-flag con env var `INVENTORY_BUS_IMPL=nats|inmemory` para rollback rápido.
     - Wave C: TODO en `services/cluster-watcher/.../InMemoryClusterEventBus.kt` se cierra.
   - Añade sección `## Riesgos`:
     - Pérdida de eventos si NATS cae antes de persist a JetStream WAL → mitigación: ACK PubAck obligatorio antes de marcar como entregado.
     - PVC corrupto en Longhorn single-replica → backup runbook obligatorio en Wave C.
     - Bumps de versión NATS → política: bump menor con keel.sh, mayor con ADR.

2. **No tocar `platform/inventory/api/InventoryEventBus.kt`** — la interfaz ya está bien diseñada en Wave A; sólo se añadirán implementaciones en Wave C.

Criterio de done:
- ADR-0005 actualizado, frontmatter válido, status=accepted.
- TaskUpdate(B1, status:completed).
- SendMessage al lead: "B1 done. ADR-0005 actualizado. backend-dev puede empezar B4."
- Continúa inmediatamente con la Task B2 sin esperar (mismo teammate).

NO ejecutes ktlint/detekt. NO crees código de producción.
```

---

## 3. Spawn: architect — Task B2 (Opus, mismo teammate que B1)

**Task B2 — ADR-0006: OTEL Collector deployment shape**

```
WORKER PREAMBLE: Sigues siendo el architect del team `fase2-observability-skeleton` (continuación de B1). NO spawnes otros agentes.

Bloqueo: blockedBy B1 (mismo teammate, secuencial). No verifica nada — simplemente continúa.

Lee antes de empezar:
- `ARCHITECTURE.md` §3 (observability), §6 (presupuesto OTEL Collector 100-200 MB).
- `.claude/rules/modulith-rules.md` §"observability (Fase 2)".
- `docs/adrs/0001-spring-modulith-vs-microservices.md` (decisión monolito).
- OpenTelemetry Collector docs (general knowledge — receivers OTLP/Prometheus, processors batch/memory_limiter, exporters Prometheus Remote Write/Loki).

Entregables (commit `docs(adr): otel collector deployment shape`):

1. **`docs/adrs/0006-otel-collector-deployment-shape.md`** siguiendo `docs/_template.md`:
   - Frontmatter: `title: "OTEL Collector deployment shape"`, `type: adr`, `owner: pablo`, `source-of-truth: "k8s/helm/otel-collector/values.yaml"`, `last-verified: 2026-05-13`, `tags: [adr, observability, otel, phase-2]`, `status: accepted`.

2. **Contexto**: el cluster necesita centralizar telemetría de aplicaciones Kotlin (platform-monolith, cluster-watcher) + futuros servicios Python (rag-ingestor, rag-query). Stack target: VictoriaMetrics (métricas), Loki (logs), Grafana (visualización). Decisión: OTEL Collector como pipeline unificada.

3. **Decisión clave 1 — Sidecar vs DaemonSet vs Deployment**:
   - Opciones evaluadas: (a) sidecar inyectado por mutating webhook, (b) DaemonSet 1 collector por nodo, (c) Deployment central con 1-2 réplicas.
   - **Elegido**: **Deployment central** con 1 réplica en Wave B (HA en Wave C+ cuando haya 3 nodes).
   - Racional: cluster single-node kubeadm v1.32.3; sidecar tiene overhead por pod (10+ pods se traducen en 10+ collectors a 50 MB c/u = 500 MB, supera el presupuesto); DaemonSet idéntico problema en single-node; Deployment central encaja en presupuesto 100-200 MB §6.
   - Trade-off: SPOF en Wave B (mitigado con `restartPolicy: Always` y readiness probe; alertas en Wave C).

4. **Decisión clave 2 — Receivers, processors, exporters**:
   - Receivers: `otlp` (gRPC :4317, HTTP :4318), `prometheus` (scrape /actuator/prometheus de servicios Kotlin con anotaciones service discovery), `filelog` (lectura de stdout vía kubelet logs — opcional, defer a Wave C si complica).
   - Processors: `memory_limiter` (check_interval 1s, limit_mib 180), `batch` (timeout 10s, send_batch_size 8192), `k8sattributes` (enriquece traces con namespace/pod/labels).
   - Exporters: `prometheusremotewrite` → VictoriaMetrics endpoint, `loki` → Loki endpoint, `debug` (sólo en values-dev.yaml para troubleshooting).
   - Pipelines: `metrics: [otlp,prometheus] → [memory_limiter,batch] → [prometheusremotewrite]`, `logs: [otlp,filelog] → [memory_limiter,k8sattributes,batch] → [loki]`, `traces: defer Wave C+`.

5. **Decisión clave 3 — Presupuesto**:
   - RAM request 100 Mi, limit 200 Mi.
   - CPU request 100m, limit 500m.
   - Sin PVC (collector es stateless; los datos persisten en VictoriaMetrics/Loki).

6. **Decisión clave 4 — Stack downstream**:
   - VictoriaMetrics single-node: 300-500 MB RAM, PVC 20 Gi Longhorn.
   - Loki single-node con filesystem backend (S3 sería mejor pero no hay MinIO en Wave B): 300-500 MB RAM, PVC 50 Gi Longhorn.
   - Grafana: 100-200 MB RAM, sin PVC (config en ConfigMap).
   - Total stack observability: ~1.5 GB RAM + 70 Gi storage. Cabe en presupuesto ARCHITECTURE.md §6 (~2 GB para todo observability).

7. **Riesgos y mitigaciones**: detallar 4-5 riesgos (collector OOMKill bajo backpressure → memory_limiter; pérdida de logs si Loki rechaza → buffer en collector; cardinalidad explosiva en métricas → relabel_configs; etc.).

8. **Plan de migración**:
   - Wave B: despliegue del stack vacío + dashboards básicos importados desde Grafana.com.
   - Wave C: instrumentación de platform-monolith y cluster-watcher (Spring Boot Actuator + Micrometer + opentelemetry-spring-boot-starter).
   - Wave D: alertas con VMAlert + integración con `observability` module del monolito.

Criterio de done:
- ADR-0006 escrito, frontmatter completo, status=accepted.
- TaskUpdate(B2, status:completed).
- SendMessage al lead: "B2 done. ADR-0006 publicado. devops-engineer puede empezar B3."

NO ejecutes ktlint/detekt. NO crees Helm charts (eso es del devops en B3). NO toques código de producción.
```

---

## 4. Spawn: devops-engineer (Sonnet)

**Task B3 — Helm charts NATS + OTEL Collector + VictoriaMetrics + Loki + Grafana**

```
WORKER PREAMBLE: Eres un agente WORKER devops-engineer del team `fase2-observability-skeleton`. NO spawnes otros agentes. Reporta al lead vía SendMessage.

Tu ownership (de `.claude/rules/ownership.md`): `.github/workflows/**`, `k8s/**`, `Dockerfile`, `routines/**`, `scripts/**`, `.env.example`. NO tocas código Kotlin, docs (excepto `docs/operations/` que también es tuyo para runbooks técnicos), tests.

Bloqueo: blockedBy B2 (necesitas las decisiones de ADR-0006 firmadas). Lee:
- `docs/adrs/0005-nats-jetstream-event-bus.md` (sección Plan de despliegue y Dimensionamiento — escritas en B1).
- `docs/adrs/0006-otel-collector-deployment-shape.md` (escrito en B2).
- `ARCHITECTURE.md` §6 (presupuestos).
- `k8s/helm/platform/` (chart de Wave A — replica el estilo).

Entregables (commit `chore(devops): helm charts nats + otel + victoriametrics + loki + grafana`):

1. **`k8s/helm/nats/`**:
   - `Chart.yaml` (name: nats, version: 0.1.0, appVersion: "2.20.5").
   - `values.yaml` + `values-dev.yaml` con: replicas 1, image `nats:2.20.5-alpine`, JetStream enabled, resources req 200Mi/100m & limit 400Mi/500m, PVC 5 Gi Longhorn (single-replica), readiness `nats-server --check-config`, prometheus exporter sidecar opcional (`natsio/prometheus-nats-exporter:0.15.0` con scrape annotations).
   - `templates/statefulset.yaml`, `templates/service.yaml` (ClusterIP, port 4222 client + 8222 monitoring + 6222 cluster), `templates/networkpolicy.yaml` (egress sólo a sí mismo; ingress desde namespaces apptolast-platform-dev y monitoring), `templates/pvc.yaml`, `templates/serviceaccount.yaml` (sin RBAC adicional — NATS no necesita acceso a K8s API).
   - `templates/configmap-nats.conf` con jetstream stanza: `jetstream { store_dir: "/data" max_mem: 64M max_file: 4G }`, http_port 8222 para metrics.

2. **`k8s/helm/otel-collector/`**:
   - `Chart.yaml` (name: otel-collector, version: 0.1.0, appVersion: "0.108.0").
   - `values.yaml` + `values-dev.yaml` con: replicas 1, image `otel/opentelemetry-collector-contrib:0.108.0`, resources req 100Mi/100m & limit 200Mi/500m, sin PVC.
   - `templates/configmap-otelcol.yaml` con la config YAML diseñada en ADR-0006 §"Decisión clave 2" (receivers otlp+prometheus, processors memory_limiter+batch+k8sattributes, exporters prometheusremotewrite a VictoriaMetrics + loki). El processor `k8sattributes` requiere RBAC.
   - `templates/deployment.yaml` con env `OTEL_RESOURCE_ATTRIBUTES="service.name=otel-collector,cluster=apptolast-kubeadm"`, args `["--config=/conf/otelcol.yaml"]`.
   - `templates/service.yaml` (ClusterIP, ports 4317 OTLP gRPC, 4318 OTLP HTTP, 8888 self-metrics).
   - `templates/serviceaccount.yaml` + `templates/clusterrole.yaml` + `templates/clusterrolebinding.yaml` — RBAC para k8sattributes processor: `get,list,watch` sobre `pods`, `namespaces`, `replicasets`.
   - `templates/networkpolicy.yaml` — ingress de cualquier namespace de apptolast a 4317/4318; egress a VictoriaMetrics + Loki + kube-apiserver.

3. **`k8s/helm/victoriametrics/`**:
   - Single-node `vmsingle`. `image: victoriametrics/victoria-metrics:v1.103.0`.
   - Resources req 300Mi/200m & limit 500Mi/1000m, PVC 20 Gi Longhorn.
   - Retention: 30 días (`-retentionPeriod=30d`).
   - Endpoint `/api/v1/write` expuesto vía Service para que otel-collector escriba.
   - `templates/{statefulset,service,pvc,networkpolicy,serviceaccount}.yaml`.

4. **`k8s/helm/loki/`**:
   - Single-binary mode (no microservices). `image: grafana/loki:3.2.0`.
   - Resources req 300Mi/200m & limit 500Mi/1000m, PVC 50 Gi Longhorn.
   - Storage backend: filesystem (`storage_config.filesystem.directory: /data/loki/chunks`). Documentar TODO migrar a S3/MinIO en Fase 4.
   - `templates/{statefulset,service,pvc,configmap-loki.yaml,networkpolicy}.yaml`.

5. **`k8s/helm/grafana/`**:
   - `image: grafana/grafana:11.2.0`.
   - Resources req 100Mi/50m & limit 200Mi/500m, sin PVC (config en ConfigMap, dashboards montados).
   - Datasources preconfigurados (vía `datasources.yaml` en ConfigMap): VictoriaMetrics (Prometheus-compatible) y Loki.
   - Dashboards iniciales: importar JSON de `k8s/helm/grafana/dashboards/` con 2 dashboards básicos: "Cluster overview" (Node Exporter Full ID 1860) y "Kubernetes / Compute Resources / Namespace (Pods)" ID 12117 — pre-bajar JSON al chart.
   - IngressRoute para `grafana.apptolast.com` con cert-manager TLS.
   - Auth: admin user vía Secret (NO en values.yaml en plaintext — usar `valueFrom.secretKeyRef`, dejar el Secret como `templates/secret-admin.yaml` con flag de uso de `.Values.adminPassword` que el operador inyecta vía `--set adminPassword=...` o `--values secrets-local.yaml` git-ignored).

6. **Extender `.github/workflows/ci.yml`**:
   - Job `helm-lint-observability` (nuevo): itera sobre `k8s/helm/{nats,otel-collector,victoriametrics,loki,grafana}` ejecutando `helm lint $chart && helm template $chart --values $chart/values-dev.yaml | kubeconform -strict -summary -`.

7. **Runbook deploy en `docs/operations/deploy-wave-b-observability.md`** (este sí es ownership devops):
   - Pasos manuales para Pablo:
     ```bash
     helm upgrade --install nats k8s/helm/nats -f k8s/helm/nats/values-dev.yaml -n nats --create-namespace
     helm upgrade --install vmsingle k8s/helm/victoriametrics -f k8s/helm/victoriametrics/values-dev.yaml -n monitoring --create-namespace
     helm upgrade --install loki k8s/helm/loki -f k8s/helm/loki/values-dev.yaml -n monitoring
     helm upgrade --install otel-collector k8s/helm/otel-collector -f k8s/helm/otel-collector/values-dev.yaml -n monitoring
     helm upgrade --install grafana k8s/helm/grafana -f k8s/helm/grafana/values-dev.yaml -n monitoring --set adminPassword=$(pass apptolast/grafana-admin)
     ```
   - Verificación: `kubectl -n nats exec sts/nats-0 -- nats-server --check-config`, `kubectl -n monitoring port-forward svc/grafana 3000:3000` y abrir browser.

Criterio de done:
- 5 charts pasan `helm lint`.
- 5 charts pasan `helm template ... | kubeconform -`.
- CI verde en branch de la wave.
- Runbook escrito en `docs/operations/deploy-wave-b-observability.md`.
- TaskUpdate(B3, status:completed).
- SendMessage al lead: "B3 done. Stack observability listo para deploy. NO he hecho `helm install` real (eso es decisión tuya)."

NO ejecutes `helm install` real contra el cluster sin orden explícita del lead. NO ejecutes ktlint/detekt. Si necesitas un dashboard JSON específico que no está en Grafana.com, anótalo como TODO y déjalo vacío.
```

---

## 5. Spawn: backend-dev (Sonnet)

**Task B4 — Skeleton `platform/observability` (api/ + application/ + package-info.java)**

```
WORKER PREAMBLE: Eres un agente WORKER backend-dev del team `fase2-observability-skeleton`. NO spawnes otros agentes. Reporta al lead vía SendMessage.

Tu ownership: `platform/observability/{api,application,domain,infrastructure}/**` y Flyway de observability. NO tocas `platform/inventory/**` (ya merged en Wave A), frontend, tests (qa-engineer), CI/CD, docs.

Bloqueo: blockedBy B1 (necesitas que ADR-0005 esté en estado `accepted` para validar la dirección de la dependencia inventory ↔ observability).

Lee antes de empezar:
- `.claude/rules/modulith-rules.md` §"observability (Fase 2)" — `allowedDependencies = {"inventory"}`.
- `platform/inventory/api/**` (de Wave A — sabrás qué eventos consume observability: `ResourceChanged`).
- `ARCHITECTURE.md` §3 (observability), §5 (patrones).
- `docs/adrs/0006-otel-collector-deployment-shape.md` (escrito en B2 — contexto downstream).
- `platform/inventory/src/main/java/com/apptolast/platform/inventory/package-info.java` (Wave A — formato de package-info esperado).

⚠️ ALCANCE LIMITADO: esta task crea SÓLO el SKELETON. NO implementes adaptadores reales (NO NATS consumer, NO VictoriaMetrics scrape, NO Loki sink). Esos llegan en Wave C cuando el stack esté desplegado y emitiendo. Crea estructura + interfaces + casos de uso vacíos con KDoc TODO referenciando Wave C.

Entregables (commit `feat(observability): module skeleton (api + application) for phase-2`):

1. **`platform/observability/src/main/java/com/apptolast/platform/observability/package-info.java`**:
   ```java
   @org.springframework.modulith.ApplicationModule(
       displayName = "Observability",
       allowedDependencies = {"inventory"}
   )
   package com.apptolast.platform.observability;
   ```

2. **`platform/observability/api/`**:
   - `ObservabilityPort.kt` — interfaz con signatures (sin impl): `getAlertsByNamespace(ns: Namespace): List<AlertSummary>`, `getMetricSeries(metric: String, ns: Namespace, range: TimeRange): MetricSeries`. KDoc: "Implementación en Wave C — ver ADR-0006."
   - `events/AlertRaised.kt`, `events/AlertResolved.kt` con `@DomainEvent`.
   - `commands/AcknowledgeAlertCommand.kt`.

3. **`platform/observability/domain/`** (Kotlin puro):
   - Entidades: `Alert` (id, severity: Severity, namespace, resource: ResourceRef, message, raisedAt, acknowledgedBy: UserRef?), `MetricSeries` (name, labels, points: List<MetricPoint>), `MetricPoint` (timestamp, value: Double).
   - Value objects: `Severity` (enum: critical, warning, info), `TimeRange` (from: Instant, to: Instant), `UserRef`.
   - **NO** importes `org.springframework.*` excepto `@DomainEvent`/`@Externalized` (verificable por ArchUnit de Wave A).

4. **`platform/observability/application/`** (con `@Service` permitido):
   - `AcknowledgeAlertUseCase` — vacío con TODO referenciando Wave D (cuando haya UI de alerts).
   - `EnrichAlertWithTopologyUseCase` — recibe `Alert`, consulta `InventoryPort` (única dependencia inter-módulo permitida) para anotar la topología afectada (¿qué pods/services rodean al resource alertado?). KDoc TODO Wave C para implementación real.
   - `ConsumeResourceChangedEventListener` — anotado `@ApplicationModuleListener` (Spring Modulith), recibe `ResourceChanged` de `platform/inventory/api/events/` y por ahora SÓLO loggea. TODO Wave C: correlacionar con métricas.

5. **`platform/observability/infrastructure/` (stubs)**:
   - `ObservabilityController.kt` con un endpoint `GET /api/v1/observability/alerts` que devuelve lista vacía (`@RestController`, `@RequestMapping("/api/v1/observability")`). Comentario: "Stub Wave B — implementación Wave C."
   - **NO** crees adaptadores reales para VictoriaMetrics/Loki/NATS. Sólo el controller stub.

6. **Flyway `platform/observability/src/main/resources/db/migration/V1__observability_init.sql`**:
   - `CREATE SCHEMA observability;`
   - Tablas mínimas: `alerts` (id PK, severity, namespace, resource_id, message, raised_at, acknowledged_at, acknowledged_by) con índice en (namespace, raised_at).

7. **`application.yml` slice**: añadir `spring.flyway.schemas=inventory,observability` (ya estaba inventory; añade observability).

Criterio de done:
- `cd platform && ./gradlew :observability:build --no-daemon` verde.
- `./gradlew :platform-app:test --tests "*Modulith*" --no-daemon` verde — esto valida que `allowedDependencies={"inventory"}` se respeta. ⚠️ Si falla con "Module 'observability' depends on non-exposed type X de inventory" → arregla la dependencia para que pase SÓLO por `inventory/api/` (NUNCA por domain/application/infrastructure).
- Aplicación arranca: `./gradlew :platform-app:bootRun` + `curl localhost:8080/api/v1/observability/alerts` devuelve `[]`.
- TaskUpdate(B4, status:completed).
- SendMessage al lead: "B4 done. observability skeleton listo. ModulithVerificationTest verde. Adaptadores reales pendientes Wave C."

NO implementes adaptadores reales (NATS, VictoriaMetrics, Loki) — esos requieren que el stack esté desplegado en cluster, lo cual es responsabilidad de Pablo tras Wave B merge. NO toques inventory. NO ejecutes ktlint/detekt.
```

---

## 6. Spawn: tech-writer (Sonnet)

**Task B5 — Actualizar `ARCHITECTURE.md` + `docs/modules/observability.md` skeleton**

```
WORKER PREAMBLE: Eres un agente WORKER tech-writer del team `fase2-observability-skeleton`. NO spawnes otros agentes. Reporta al lead vía SendMessage.

Tu ownership: `docs/**` excepto `docs/adrs/**` (architect) y `docs/security/**` (security-reviewer), `README.md`, `CHANGELOG.md`, KDoc en código (sin cambiar lógica). NO tocas código Kotlin ni helm charts.

Bloqueo: blockedBy B1 y B3 (necesitas las decisiones de los ADRs y los charts existentes para describir el estado). Puede correr en paralelo con B4. Lee:
- `docs/_template.md`.
- `docs/adrs/0005-nats-jetstream-event-bus.md` (actualizado en B1).
- `docs/adrs/0006-otel-collector-deployment-shape.md` (creado en B2).
- `k8s/helm/{nats,otel-collector,victoriametrics,loki,grafana}/` (creados en B3 — al menos los `values.yaml` para verificar dimensionamiento).
- `ARCHITECTURE.md` (versión actual).
- `docs/modules/inventory.md` (Wave A — base de estilo).

Entregables (commit `docs(observability): module skeleton overview + architecture update`):

1. **Actualizar `ARCHITECTURE.md` §4 (Roadmap)**:
   - Marcar Wave A de Fase 1 como `merged` con fecha 2026-05-13.
   - Marcar Wave B de Fase 2 como `in-progress` con fecha 2026-05-13.
   - Listar tareas pendientes para cerrar Fase 2 (Wave C: implementar adaptadores NATS + VictoriaMetrics + Loki en monolito; Wave D: alertas y UI de observability).

2. **Actualizar `ARCHITECTURE.md` §3 (Módulos)**:
   - Añadir entrada `observability` con: `status: skeleton`, `allowedDependencies: ["inventory"]`, eventos publicados (`AlertRaised`, `AlertResolved`), eventos consumidos (`ResourceChanged` de inventory + métricas vía OTEL Collector pendiente Wave C).

3. **Actualizar `ARCHITECTURE.md` §6 (Presupuestos)**:
   - Añadir/actualizar entradas: NATS 200-400 MB + 5 Gi PVC, OTEL Collector 100-200 MB, VictoriaMetrics 300-500 MB + 20 Gi PVC, Loki 300-500 MB + 50 Gi PVC, Grafana 100-200 MB.
   - Tabla actualizada con totales actuales del cluster (cita: kubeadm v1.32.3, 126 pods, ~estos nuevos sumarán ~1.5 GB RAM + 75 Gi storage).

4. **`docs/modules/observability.md`** siguiendo `docs/_template.md`:
   - Frontmatter:
     - `title: "Módulo Observability"`
     - `type: module-overview`
     - `owner: pablo`
     - `source-of-truth: "kubectl get pods,svc -n monitoring && curl -s http://vmsingle:8428/api/v1/status/tsdb | jq"`
     - `last-verified: 2026-05-13`
     - `tags: [module, observability, otel, victoriametrics, loki, phase-2, skeleton]`
     - `status: beta` (skeleton — adaptadores pendientes Wave C)
     - `depends-on: [namespace:monitoring, service:nats, service:otel-collector, service:vmsingle, service:loki, service:grafana, module:inventory]`
     - `used-by: []` (futuro: automation, knowledge)
     - `see-also: [docs/adrs/0005-nats-jetstream-event-bus.md, docs/adrs/0006-otel-collector-deployment-shape.md, docs/modules/inventory.md]`
   - Contenido:
     - **Resumen**: módulo observability del monolito + stack downstream (NATS, OTEL Collector, VictoriaMetrics, Loki, Grafana).
     - **Contexto / Por qué existe**: necesidad de centralizar métricas/logs/alertas del cluster y del propio IDP; correlación con topología de inventory.
     - **Estado actual (verificado 2026-05-13)**: SKELETON. Módulo del monolito tiene api/+application/+stub controller; sin adaptadores reales aún. Stack downstream desplegable via Helm (no necesariamente deployed). Wave C cierra los adaptadores.
     - **Topología**: diagrama ASCII o mermaid con: `[Services Kotlin] --otlp--> [OTEL Collector] --remotewrite--> [VictoriaMetrics]` + `... --loki sink--> [Loki]` + `[NATS JetStream] <--ResourceChanged-- [inventory]` + `[observability module] --reads--> [VictoriaMetrics, Loki]`.
     - **Contratos (`api/`)**: lista `ObservabilityPort`, eventos.
     - **Cómo se despliega**: link a `docs/operations/deploy-wave-b-observability.md` (escrito por devops en B3).
     - **Roadmap interno**:
       - Wave B (esta wave): skeleton merged + stack downstream con Helm charts.
       - Wave C: adaptadores NATS + VictoriaMetrics + Loki en monolito; instrumentación de platform-monolith y cluster-watcher.
       - Wave D: alertas (VMAlert + AlertRaised/Resolved); UI alerts en frontend.
     - **Troubleshooting básico**: tabla síntoma → diagnóstico → fix. Casos: OTEL Collector OOMKill (revisar memory_limiter), Loki rechaza por cardinality (revisar labels), métrica no aparece (verificar scrape annotation), NATS PVC corrupto (Wave C runbook).
     - **Histórico relevante**: `2026-05-13 — Skeleton creado en Wave B de Fase 2.`
     - **Referencias**: ADR-0005, ADR-0006, ARCHITECTURE.md §3 §6.

5. **Actualizar `CHANGELOG.md`**:
   - Nueva entrada en `[Unreleased]`:
     - `### Added`: módulo `observability` (skeleton), ADR-0006, Helm charts NATS + OTEL Collector + VictoriaMetrics + Loki + Grafana, runbook deploy-wave-b-observability.md.
     - `### Changed`: ADR-0005 status → accepted (deployed-version: 2.20.5); ARCHITECTURE.md §3 §4 §6 actualizadas.

6. **Actualizar `README.md`** sección "Estado actual":
   - Marca `inventory` como `stable` (Wave A merged hace tiempo según cronología del proyecto).
   - Añade `observability` como `beta (skeleton)`.
   - Lista los servicios de monitoring desplegables.

Criterio de done:
- ARCHITECTURE.md, README.md, CHANGELOG.md, docs/modules/observability.md commited y consistentes entre sí.
- Frontmatter validado contra `docs/_template.md`.
- TaskUpdate(B5, status:completed).
- SendMessage al lead: "B5 done. Documentación de Wave B sincronizada."

NO modifiques `docs/adrs/**` ni `docs/security/**`. NO toques código Kotlin ni helm charts. NO ejecutes ktlint/detekt.
```

---

## 7. Cleanup al final de Wave B

```
Lead: ejecuta cuando TaskList muestre B1..B5 en `status:completed`:

1. Sintetiza la wave en `docs/progress/2026-W20.md` (asumiendo Wave B una semana después de Wave A; ajustar al week ISO real). Incluye: ADRs publicados, charts Helm preparados, módulo observability esqueleto, TODOs explícitos para Wave C (adaptadores NATS + scrape métricas + sink logs).

2. SendMessage a cada teammate (architect, backend-dev, devops-engineer, tech-writer):
   "Wave B complete. Gracias. Recordatorio: el deploy real del stack al cluster es decisión de Pablo, no automática. Shutdown OK."

3. Espera confirmación de shutdown. Fuerza TaskStop si > 5 min sin respuesta.

4. Cleanup del team: `TeamDelete fase2-observability-skeleton`.

5. Stage + commit final:
   git add platform/observability/ k8s/helm/nats/ k8s/helm/otel-collector/ k8s/helm/victoriametrics/ k8s/helm/loki/ k8s/helm/grafana/ docs/adrs/0005-nats-jetstream-event-bus.md docs/adrs/0006-otel-collector-deployment-shape.md docs/modules/observability.md docs/operations/deploy-wave-b-observability.md docs/progress/2026-W20.md ARCHITECTURE.md README.md CHANGELOG.md .github/workflows/ci.yml
   git commit -m "$(cat <<'EOF'
feat(phase-2): nats + observability skeleton + helm charts

Wave B de Fase 2: ADR-0005 actualizado (NATS deployment),
ADR-0006 (OTEL Collector deployment shape), Helm charts
para NATS + OTEL Collector + VictoriaMetrics + Loki +
Grafana, módulo platform/observability esqueleto con
allowedDependencies={"inventory"} validado por Modulith.

Adaptadores reales (NATS consumer, scrape, sink) llegan
en Wave C cuando el stack esté desplegado.
EOF
)"

6. Push y PR draft:
   git push -u origin phase-2-wave-b
   gh pr create --draft --base main --title "Phase 2 — NATS + observability skeleton + helm charts" --body "$(cat <<'EOF'
## Summary

- ADR-0005 actualizado a `status: accepted` con plan de despliegue NATS y dimensionamiento.
- ADR-0006 (nuevo): OTEL Collector deployment shape — Deployment central elegido sobre sidecar/DaemonSet.
- Helm charts para NATS, OTEL Collector, VictoriaMetrics, Loki, Grafana.
- Módulo `platform/observability` esqueleto con `allowedDependencies={"inventory"}`.
- Runbook `docs/operations/deploy-wave-b-observability.md` para el deploy manual de Pablo.

## Test plan

- [ ] `./gradlew :observability:build` verde
- [ ] `./gradlew :platform-app:test --tests "*Modulith*"` verde (valida allowedDependencies)
- [ ] `helm lint k8s/helm/{nats,otel-collector,victoriametrics,loki,grafana}` los 5 verdes
- [ ] `helm template ... | kubeconform -` los 5 verdes
- [ ] CI GitHub Actions verde con nuevo job `helm-lint-observability`
- [ ] (Manual, no automatizado) deploy en cluster siguiendo `docs/operations/deploy-wave-b-observability.md`

EOF
)"
```

---

## Verificación de done (ejecutable por Pablo tras merge)

```bash
cd /home/admin/sistema-central-admin-servidor/

# Build del nuevo módulo
cd platform && ./gradlew :observability:build --no-daemon

# Modulith verify (allowedDependencies={"inventory"} validado)
./gradlew :platform-app:test --tests "*Modulith*" --no-daemon

# Lint de los 5 charts
for chart in nats otel-collector victoriametrics loki grafana; do
  helm lint ../k8s/helm/$chart || echo "FAIL: $chart"
  helm template ../k8s/helm/$chart --values ../k8s/helm/$chart/values-dev.yaml | kubeconform -strict -summary - || echo "FAIL kubeconform: $chart"
done

# Smoke local del monolito con endpoint observability
./gradlew :platform-app:bootRun &
BOOTRUN_PID=$!
until curl -fsS http://localhost:8080/actuator/health/readiness > /dev/null 2>&1; do sleep 2; done
curl -fsS http://localhost:8080/api/v1/observability/alerts | jq '. | length'  # esperado: 0 (stub)
kill $BOOTRUN_PID

# Deploy manual al cluster (decisión de Pablo — NO automático):
# helm upgrade --install nats ../k8s/helm/nats -f ../k8s/helm/nats/values-dev.yaml -n nats --create-namespace
# ... (ver docs/operations/deploy-wave-b-observability.md)
```

---

## Troubleshooting

**`ModulithVerificationTest` falla con `Module 'observability' depends on non-exposed type X of 'inventory'`.**
- Razón: algún caso de uso o adaptador del módulo observability importó algo desde `platform/inventory/domain/` o `platform/inventory/application/` en lugar de pasar por `platform/inventory/api/`.
- Fix: usar SÓLO `InventoryPort` o eventos publicados en `api/events/`. Si necesitas algo más, pídeselo al architect — puede que requiera ampliar `inventory/api/`.

**NATS pod `CrashLoopBackOff` tras `helm install`.**
- Razón: PVC Longhorn no provisionado o storageClass incorrecto.
- Fix: `kubectl get pvc -n nats` para ver el estado. Verificar `values-dev.yaml` que `storageClassName` sea el correcto (`longhorn` típicamente). Borrar PVC y dejar que se recree si está stuck.

**OTEL Collector OOMKill con backpressure.**
- Razón: `memory_limiter` mal calibrado o exporters bloqueados.
- Fix: revisar `processors.memory_limiter.limit_mib` (debe ser ~90% del limit del container). Aumentar buffer del exporter o el resource limit.

**Grafana datasource VictoriaMetrics no conecta.**
- Razón: URL incorrecta en `datasources.yaml` o NetworkPolicy bloquea.
- Fix: usar URL interna del cluster `http://vmsingle.monitoring.svc.cluster.local:8428`. Verificar `kubectl exec` desde el pod de Grafana: `wget -qO- http://vmsingle.monitoring.svc.cluster.local:8428/api/v1/status/tsdb`.

**Loki rechaza logs por "too many streams".**
- Razón: cardinalidad alta de labels (label `pod_name` o `container_id` dinámico).
- Fix: en `otel-collector` config añadir un processor `attributes/loki` que filtre labels de alta cardinalidad antes del exporter loki. Documentar la decisión en KDoc.

**`helm install` falla con "namespace already exists with different labels".**
- Razón: namespace creado manualmente sin labels esperadas por el chart.
- Fix: `kubectl label namespace monitoring app.kubernetes.io/managed-by=Helm` o usar `--create-namespace` desde el primer install.

**Compaction perdió el contexto del lead.**
- Fix: relee `CLAUDE.md`, `ARCHITECTURE.md`, `docs/adrs/0005-*`, `docs/adrs/0006-*` y ejecuta `TaskList`. Si los teammates están vivos, reconstruye estado desde sus últimos SendMessage.

**Wave B mergeada pero NATS no funciona en cluster tras deploy real.**
- Razón: el `helm install` real NO es parte de la wave (es decisión manual de Pablo). Los teammates de Wave B sólo escribieron y validaron charts.
- Fix: seguir el runbook `docs/operations/deploy-wave-b-observability.md`. Si hay error, abrir issue y planificar hotfix antes de Wave C.
