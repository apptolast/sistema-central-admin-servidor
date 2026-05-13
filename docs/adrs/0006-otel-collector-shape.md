---
title: "ADR-0006: OTEL Collector deployment shape para single-node Hetzner"
type: adr
owner: pablo
source-of-truth: "docs/adrs/0006-otel-collector-shape.md"
last-verified: 2026-05-13
tags: [adr, observability, otel, victoria-metrics, loki, phase-2]
status: accepted
phase: meta
related-docs:
  - ARCHITECTURE.md
  - docs/adrs/0005-nats-jetstream-vs-inmemory-bus.md
  - docs/infrastructure/cluster-baseline-2026-05-13.md
---

# ADR-0006 — OTEL Collector deployment shape para single-node Hetzner

**Estado:** accepted
**Decisores:** Pablo Hurtado
**Fecha:** 2026-05-13

## Contexto

El cluster es **single-node** (Hetzner CPX62: 16 vCPU EPYC-Genoa, 32 GB RAM
[source: docs/infrastructure/cluster-baseline-2026-05-13.md#servidor-hetzner])
con 126 pods consumiendo 20 GB RAM y disco raíz al 73%. Cualquier stack de
observabilidad nuevo tiene que **caber en menos de 200 MB de RAM y 500 MB de
disco** o no entra.

Lo que existe hoy ([source: docs/infrastructure/cluster-baseline-2026-05-13.md#observabilidad]):

- **Dozzle** (logs en vivo, sin retención)
- **Uptime Kuma** (synthetic monitoring)
- **kube-state-metrics** (instalado, sin scrape — no hay Prometheus)
- **event-exporter** (envía events a Discord)
- **homepage** (panel agregador)
- **Certs huérfanos**: `prometheus.apptolast.com`, `grafana.apptolast.com`,
  `alertmanager.apptolast.com` — los certs existen pero los pods NO

Necesitamos:
- **Métricas** time-series para apps (latencia, throughput, errors)
- **Logs** estructurados con retention semanal
- **Traces** distribuidos (Fase 3+ — RAG queries)
- **Alerting** vía Discord/Telegram (ya tenemos webhooks configurados)

Opciones evaluadas:

### A. Prometheus + Loki + Grafana + Alertmanager (Helm chart kube-prometheus-stack)

- Industry standard, mucha documentación
- **Coste RAM**: Prometheus ~600MB + Loki ~250MB + Grafana ~150MB + Alertmanager ~100MB = **~1.1GB**
- **Coste disco**: TSDB Prometheus crece ~1GB/día con scrape de 30s

### B. VictoriaMetrics + VictoriaLogs + Grafana (sin Prometheus)

- VictoriaMetrics: Prometheus-compatible API pero 10x más eficiente en RAM
- VictoriaLogs: alternativa moderna a Loki, más ligera
- **Coste RAM**: VM ~150MB + VL ~80MB + Grafana ~150MB = **~380MB**
- **Coste disco**: ~10x menos que Prometheus por las mismas series

### C. SigNoz (todo-en-uno: traces + metrics + logs en ClickHouse)

- Pre-integrado pero muy pesado
- **Coste RAM**: ~2.5GB (ClickHouse solo ya pide 1GB)
- Descartado por presupuesto

### D. OpenObserve (todo-en-uno minimalista en Rust)

- Single binary, S3-compatible storage backend
- **Coste RAM**: ~120MB
- Solo 1 mantenedor activo, ecosystem pequeño

## Decisión

**Opción B: VictoriaMetrics + VictoriaLogs + Grafana, todos detrás de un único
OTEL Collector como gateway.**

**Topología:** `OTEL Collector` como **Deployment** (NO DaemonSet), 1 réplica,
ingest gateway. Las apps mandan OTLP al collector, el collector hace fan-out a
VM (métricas) y VL (logs).

## Justificación

### Por qué Deployment en lugar de DaemonSet

DaemonSet tiene sentido en clusters multi-nodo: cada nodo tiene su propio
collector → menos tráfico cross-node + recolección de host metrics local.

**Pero aquí solo hay 1 nodo.** Un DaemonSet con 1 réplica = un Deployment con 1
réplica, pero con menos flexibilidad para horizontal scaling cuando expandamos.

Deployment 1 réplica resource-limited:
- requests: 50m CPU, 100Mi RAM
- limits: 200m CPU, 200Mi RAM

Si llegamos a 2.º nodo (Fase 7+), se convierte fácilmente en DaemonSet.

### Por qué VictoriaMetrics > Prometheus

1. **RAM**: en single-node con presupuesto < 200MB para métricas, Prometheus no
   cabe. VM single-node con 100k active series consume ~150MB.
2. **API compat**: PromQL + remote-write API completos. Grafana lee igual de los
   dos.
3. **Disk efficiency**: VictoriaMetrics deduplica y comprime con un factor 3-10x
   mejor que Prometheus TSDB. Para nuestro presupuesto de disco esto es crítico.
4. **Operación**: 1 binario, no hay sidecars, no hay long-term storage gymnastics.

### Por qué VictoriaLogs > Loki

Loki tiene buen tooling pero:
- Su modelo de label cardinality es restrictivo
- Quema CPU al hacer queries fulltext
- Requiere Grafana 10+ con el plugin Loki bien configurado

VictoriaLogs ofrece:
- LogsQL más potente que LogQL
- Sin label cardinality issues
- Mismo equipo que VM (mejor consistency operacional)

Trade-off: ecosystem más pequeño que Loki. Aceptable para uso interno.

### Por qué OTEL Collector como gateway único

Sin gateway, cada app necesitaría 2 endpoints (uno para métricas, uno para logs)
y conocer ambos formatos. Con el collector:
- Las apps envían **solo OTLP** (estándar moderno)
- El collector transforma + enruta + retry/buffer
- Cambiar de backend (ej. VM → Prometheus) requiere solo cambiar config del collector

OTEL Collector Helm chart oficial soporta esto out-of-the-box con el `mode: deployment`.

### Por qué Grafana se queda

Grafana es el único dashboard maduro que habla con ambos (VM + VL). Alternativas
(VMUI, OpenObserve UI) son menos potentes. 150MB de Grafana son aceptables.

### Alertmanager NO se despliega — usamos lo que hay

Tenemos Discord/Telegram webhooks y healthchecks.io ya configurados en
`cluster-ops/discord-webhooks`. VictoriaMetrics tiene **vmalert** built-in, que:
- Evalúa reglas PromQL
- Manda alerts a un URL configurable (puede ser un webhook Discord directamente)
- No necesita Alertmanager intermedio

Ahorro: ~100MB RAM eliminando Alertmanager.

## Presupuesto detallado

| Componente              | RAM       | CPU req | Disco PV    |
|-------------------------|-----------|---------|-------------|
| OTEL Collector (gateway)| 80 MB     | 50m     | (memory)    |
| VictoriaMetrics         | 150 MB    | 100m    | 5 GiB       |
| VictoriaLogs            | 80 MB     | 50m     | 5 GiB       |
| vmalert                 | 30 MB     | 50m     | (memory)    |
| Grafana                 | 150 MB    | 50m     | 500 MiB     |
| **Total**               | **490 MB**| 300m    | 10.5 GiB    |

Fits en el cluster (30 GB RAM, 39 GB disco libre).

## Implementación

### Helm releases a desplegar

```yaml
namespace: monitoring
releases:
  - name: otel-collector
    chart: open-telemetry/opentelemetry-collector
    values: k8s/helm/otel-collector/values.yaml
  - name: victoria-metrics
    chart: vm/victoria-metrics-single
    values: k8s/helm/victoria-metrics/values.yaml
  - name: victoria-logs
    chart: vm/victoria-logs-single
    values: k8s/helm/victoria-logs/values.yaml
  - name: grafana
    chart: grafana/grafana
    values: k8s/helm/grafana/values.yaml
```

### Config OTEL Collector (resumen)

```yaml
receivers:
  otlp:
    protocols:
      grpc: { endpoint: 0.0.0.0:4317 }
      http: { endpoint: 0.0.0.0:4318 }
  prometheus:
    config:
      scrape_configs:
        - job_name: 'kube-state-metrics'
          static_configs: [{ targets: ['kube-state-metrics.monitoring:8080'] }]

processors:
  batch:
    timeout: 10s
    send_batch_size: 1024
  memory_limiter:
    check_interval: 1s
    limit_mib: 180

exporters:
  prometheusremotewrite:
    endpoint: http://victoria-metrics:8428/api/v1/write
  otlphttp/logs:
    endpoint: http://victoria-logs:9428/insert/opentelemetry/v1/logs

service:
  pipelines:
    metrics:
      receivers: [otlp, prometheus]
      processors: [memory_limiter, batch]
      exporters: [prometheusremotewrite]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlphttp/logs]
```

### Auto-instrumentation en apps Spring Boot

Spring Boot 3.5 ya trae Micrometer + OTEL. Solo añadir:

```yaml
management:
  otlp:
    metrics:
      export:
        url: http://otel-collector.monitoring:4318/v1/metrics
    tracing:
      endpoint: http://otel-collector.monitoring:4318/v1/traces
  tracing:
    sampling.probability: 0.1
```

Sin agente Java externo. Sin sidecar. Sin tocar el código.

### Migración cert-manager — limpieza de certs huérfanos

Antes de desplegar Grafana, eliminar los certs huérfanos en `monitoring`:

```bash
# Verificar que efectivamente no tienen IngressRoute apuntándolos
kubectl get certificate -n monitoring | grep -E '(prometheus|grafana|alertmanager)'

# Eliminar — el cert se renueva limpio cuando despleguemos
kubectl delete certificate -n monitoring \
  prometheus-dot-apptolast-com-tls \
  alertmanager-dot-apptolast-com-tls

# grafana-dot-apptolast-com-tls SE REUSA por el Grafana real cuando aterrice
```

## Consecuencias

### Positivas

- Stack moderno con OTEL como interfaz estándar — no estamos atados a VM/VL.
- Cabe holgado en el cluster con presupuesto sub-500MB.
- Vmalert reemplaza Alertmanager y se conecta directo a los webhooks que ya tenemos.
- Logs estructurados queryable, no solo Dozzle live tail.

### Negativas

- VictoriaLogs tiene ecosistema menor que Loki — menos plugins/docs.
- Aprendizaje de LogsQL (similar pero no idéntico a LogQL).
- Si VM crece más allá de 100k series, el cluster será limitante. Plan: en Fase 7+
  expansión a 2.º nodo Hetzner separa monitoring.

### Riesgos

- **R1**: pérdida de retención si VM tiene un crash sin backup → mitigación:
  Longhorn snapshots diarios del PVC.
- **R2**: cardinalidad explota si una app etiqueta métricas con `pod_name`
  efímero → mitigación: relabel en OTEL Collector descarta labels efímeros.
- **R3**: vmalert no soporta routing complejo (ej. silenciar alerts por horario)
  → mitigación: si necesitamos eso, añadir Alertmanager en Fase 7.

## Métricas de éxito

- RAM monitoring stack < 500 MB sostenido (verificar 1 semana post-deploy)
- p99 query time Grafana dashboards < 2s
- 100% de cluster-ops cronjobs reportan métricas/logs a OTEL en lugar de Discord directo

## Referencias

- VictoriaMetrics docs: https://docs.victoriametrics.com/
- OpenTelemetry Collector: https://opentelemetry.io/docs/collector/
- vmalert: https://docs.victoriametrics.com/vmalert/
- Spring Boot 3.5 OTLP: https://docs.spring.io/spring-boot/reference/actuator/observability.html

## Citation footer

- Cluster size: [source: docs/infrastructure/cluster-baseline-2026-05-13.md#servidor-hetzner@HEAD]
- Observability gaps actuales: [source: docs/infrastructure/cluster-baseline-2026-05-13.md#observabilidad@HEAD]
- Certs huérfanos a limpiar: [source: docs/infrastructure/cluster-baseline-2026-05-13.md#networking@HEAD]
