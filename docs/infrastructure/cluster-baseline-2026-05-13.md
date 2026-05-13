---
title: "Cluster baseline snapshot — 2026-05-13"
type: infrastructure
owner: pablo
source-of-truth: "kubectl get all -A + helm list -A + dmidecode + df + crontab -l"
last-verified: 2026-05-13
tags: [baseline, cluster, hetzner, kubernetes, dossier]
status: stable
phase: meta
depends-on:
  - host:AppToLastServer
  - network:138.199.157.58
related-runbooks:
  - RB-01_HOST_DISK_HIGH
  - RB-02_HOST_RAM_HIGH
  - RB-17_LONGHORN_DEGRADED
  - LONGHORN_BACKUP_TARGET
  - NODEPORT_EXPOSURE_AUDIT
---

# Cluster baseline — 2026-05-13

> Snapshot consolidado del estado del cluster `apptolastserver` capturado en una sesión de Claude Code el 2026-05-12 ~21:10 UTC y revalidado parcialmente el 2026-05-13. Fuente directa: ejecución de `kubectl`, `helm`, `dmidecode`, `df`, `crontab`, `systemctl`, `gh` contra el servidor real.
>
> Este documento es la referencia citable (`[source: docs/infrastructure/cluster-baseline-2026-05-13.md#<sección>@<sha>]`) para cualquier afirmación factual sobre la infra que aparezca en docs, ADRs, marathon-plan, wave prompts o respuestas RAG.

## 1. Servidor Hetzner

| Campo | Valor |
|---|---|
| Hostname | `AppToLastServer` |
| Tipo | Hetzner Cloud vServer **CPX62** |
| Datacenter | Falkenstein, Germany (`eu-central`) |
| IPv4 pública | `138.199.157.58/32` |
| IPv6 pública | `2a01:4f8:c013:b7d4::/64` |
| Gateway | `172.31.1.1` (eth0, dhcp) |
| DNS recursive | `185.12.64.2`, `2a01:4ff:ff00::add:2/1` (Hetzner) |
| CPU | AMD EPYC-Genoa, 16 vCPU (1 socket × 16 cores) |
| RAM | 30 GiB (32088 MB), uso ~20 GiB, dispo ~10 GiB + 10 GiB buff/cache |
| Swap | 4 GiB (swapfile, swappiness=10) |
| OS | Ubuntu 24.04.4 LTS |
| Kernel | 6.8.0-106-generic (System restart required pending 88-94 updates) |
| Virt | KVM |
| Tráfico mensual | 20 TB outbound incluidos |
| Precio | ~61.09 €/mes |
| Backups Hetzner | **Disabled** |

### Discos

| Device | Size | FS | Mount | Uso |
|---|---|---|---|---|
| sda | 152.6 GB | ext4 | `/` | 105G/150G = **73%** |
| sda15 | 256 MB | vfat | `/boot/efi` | 6.2M/253M = 3% |
| sdb | 200 GB | ext4 | `/mnt/HC_Volume_103965858` | 72G/196G = 39% |
| sd[c-aw] | varios | ext4 | `/var/lib/kubelet/.../mount/` | 30 volúmenes Longhorn vía iSCSI |

Reparto del 73% en `/`:
- `/var/lib/containerd`: 48 GiB (imágenes y snapshots — ninguna GCable)
- `/var/lib/kubelet`: 22 GiB
- `/home`: 29 GiB (repos clonados, n8n-backup)
- `/var/log`: 2.0 GiB

Reparto del HC Volume (`/mnt/HC_Volume_103965858`):
- `longhorn/`: 69 GiB (data path real de Longhorn)
- `backups/`: 2.6 GiB
- `rancher-backups/`: 96 MiB

## 2. Cluster Kubernetes

| Campo | Valor |
|---|---|
| Distribución | **kubeadm** (NO RKE2) |
| Versión | v1.32.3 (cliente y servidor) |
| CRI | containerd 2.2.2 |
| CNI | Calico (IPIP, /16) |
| Pod CIDR | `10.244.0.0/16` (nodo: `10.244.198.128/26` + `.192/26`) |
| Service CIDR | `10.96.0.0/16` |
| Nodos | 1 (`apptolastserver`, control-plane sin taint) |
| Pods totales | 126 en 36 namespaces |
| Rancher | v2.11.1 importado como cluster local |
| Static pods | etcd, kube-apiserver, kube-controller-manager, kube-scheduler en `/etc/kubernetes/manifests/` |

### Distribución de pods por namespace (top)

```
19  longhorn-system
15  cluster-ops              ← sistema autónomo, 18 cronjobs
10  kube-system
 8  apptolast-invernadero-api
 7  n8n
 6  apptolast-menus-dev
 5  openclaw
 5  apptolast-inemsellar
 5  apptolast-greenhouse-admin-dev
 4  gibbon
 3  langflow / fleet-default / cert-manager
 2  shlink / passbolt / monitoring / metallb-system / cattle-system / cattle-fleet-system / apptolast-invernadero-api-prod
 1  traefik / redisinsight / portfolio-web-pablohg / personal-website-alberto / minecraft-stats / minecraft / keel / health-dashboard / ficsitmonitor / default
```

### Eventos Warning recientes

Único repetido en última hora:
```
FreeDiskSpaceFailed: Failed to garbage collect required amount of images.
  Attempted to free 30688637747 bytes, but only found 0 bytes eligible to free.
```
→ kubelet quiere liberar ~30G en `/var/lib/containerd` pero todas las imágenes están en uso. No urgente, pero **bandera amarilla** — cuando cruce 85% empieza eviction.

## 3. Helm releases

| Release | Namespace | Chart/version | App ver | Estado |
|---|---|---|---|---|
| cert-manager | cert-manager | cert-manager v1.17.2 | v1.17.2 | deployed |
| fleet | cattle-fleet-system | fleet 106.1.0+up0.12.2 | 0.12.2 | deployed |
| fleet-agent-local | cattle-fleet-local-system | fleet-agent-local | — | deployed |
| fleet-crd | cattle-fleet-system | fleet-crd 106.1.0+up0.12.2 | 0.12.2 | deployed |
| keel | keel | keel 1.0.5 | 0.19.1 | deployed |
| kube-state-metrics | monitoring | kube-state-metrics 7.2.1 | 2.18.0 | deployed |
| kuma | cluster-ops | uptime-kuma 4.1.0 | 2.3.0 | deployed |
| langflow-ide | langflow | langflow-ide 0.1.1 | latest | deployed |
| metallb | metallb-system | metallb 0.14.9 | v0.14.9 | deployed |
| metrics-server | kube-system | metrics-server 3.13.0 | 0.8.0 | deployed |
| **n8n-prod** | n8n | n8n 2.0.1 | 1.122.4 | ⚠️ **failed** (desde 2025-12-22) |
| postgresql-langflow | langflow | postgresql 18.1.1 | 18.0.0 | deployed |
| rancher | cattle-system | rancher 2.11.1 | v2.11.1 | deployed |
| rancher-provisioning-capi | cattle-provisioning-capi-system | rancher-provisioning-capi 0.7.0 | — | deployed |
| rancher-webhook | cattle-system | rancher-webhook 0.7.1 | — | deployed |
| traefik | traefik | traefik 35.2.0 | v3.3.6 | deployed |
| traefik-crds | default | traefik 35.2.0 | v3.3.6 | deployed (legacy duplicado) |

## 4. Storage — Longhorn

| StorageClass | Provisioner | Reclaim | Binding | Expansion |
|---|---|---|---|---|
| local-storage | kubernetes.io/no-provisioner | Retain | WaitForFirstConsumer | false |
| longhorn | driver.longhorn.io | Delete | Immediate | true |
| **longhorn-single-replica** *(default)* | driver.longhorn.io | Delete | Immediate | true |
| longhorn-static | driver.longhorn.io | Delete | Immediate | true |

Settings clave:
- `default-replica-count = 1`
- `storage-over-provisioning-percentage = 200`
- `backup-target = ""` ⚠️ **NO HAY BACKUP REMOTO**
- `backup-target-credential-secret = ""`

30 volúmenes Longhorn (28 attached/healthy, 2 detached intermitentes, 1 degraded by-design): ~165 GiB asignados, ~72 GiB usados en HC Volume.

PVs Available huérfanos (local-storage, sin claim): `emqx-pv` (5G), `postgresql-metadata-pv` (20G), `redis-pv` (5G), `timescaledb-pv` (20G).

## 5. Networking

### Traefik

- Helm release: `traefik` 35.2.0 → Traefik v3.3.6
- Service LoadBalancer → MetalLB → `138.199.157.58`
- IngressRoutes: **35**
- IngressRouteTCP: 0
- Middlewares: 32 (security-headers, basicauth, ratelimit, redirect-https)

### MetalLB

- Pool default: `138.199.157.58/32` (un único IP, el público del nodo)
- L2Advertisement: default

### Cloudflare DNS — apptolast.com

- **80 records** totales (50+30 según paginación del panel)
- Servidores de nombres: `clark.ns.cloudflare.com`, `rosemary.ns.cloudflare.com`
- Todos en estado "Solo DNS" (NO proxied)
- MX: Google Workspace (`aspmx.l.google.com` + alt1-4)
- `www`: CNAME → `apptolast.github.io`
- `apptolast.com` apex: A → GitHub Pages IPs (185.199.108.153, 109.153, 110.153, 111.153)
- ClusterIssuer cert-manager: `cloudflare-clusterissuer` (DNS01)

### NodePorts expuestos al internet público (sin firewall verificado)

| Servicio | NodePort | Protocolo |
|---|---|---|
| emqx-external | 30883, 30884, 30180, 30081, 30083, 30084 | TCP (MQTT, mgmt, dashboard, WS) |
| postgresql-metadata-external (invernadero-api) | 30433 | TCP (PG) |
| postgresql-external (whoop-david-api) | 30434 | TCP (PG) |
| postgres-external (inemsellar) | 30435 | TCP (PG) |
| postgres-external (menus-dev) | 30543 | TCP (PG) |
| timescaledb-external | 30432 | TCP (PG TimescaleDB) |
| redis-external (invernadero) | 30379 | TCP (Redis) |
| redis-external (menus-dev) | 30639 | TCP (Redis) |
| wireguard | 31820 | UDP (VPN) |
| sftp-service | 30022 | TCP (SFTP) |
| mcp-server-external | 30000 | TCP (HTTP) |
| minecraft | 30565, 30575 | TCP (Minecraft game/rcon) |

→ Si no hay Hetzner firewall a nivel cloud, **estos puertos están accesibles desde cualquier IP**. Dolor P1 #8 del dossier. Atacar en Fase 6.

### WireGuard

- Pod: `wireguard-XXX` en ns `apptolast-wireguard`
- Image: `ghcr.io/linuxserver/wireguard:latest` ⚠️ tag `latest` sin pin (dolor P1 #9)
- Dashboard: `wg.apptolast.com` (basicauth middleware)
- PVC: `wgdashboard-data` 1 GiB
- Host `/etc/wireguard/` VACÍO — configs viven en el PVC del pod

## 6. Bases de datos (inventario)

| Engine | Pod | Namespace | Versión | Tamaño | Backup |
|---|---|---|---|---|---|
| PostgreSQL | postgres-n8n-0 | n8n | 16.10 | 10 GiB | postgres-backups-pvc 5G (PVC sin cronjob visible) |
| PostgreSQL | postgres-vector-0 | n8n | (pgvector) | 5 GiB | — |
| PostgreSQL | postgresql-metadata-0 | apptolast-invernadero-api | — | 1 GiB | — |
| TimescaleDB | timescaledb-0 | apptolast-invernadero-api | — | 12 GiB | — |
| PostgreSQL | postgres-0 | apptolast-inemsellar | — | 5 GiB | — |
| PostgreSQL | postgres-0 | apptolast-menus-dev | — | 5 GiB | — |
| PostgreSQL | postgresql-0 | apptolast-whoop-david-api | — | 2 GiB | — |
| PostgreSQL | postgresql-langflow-0 | langflow | — | 500 MiB | — |
| PostgreSQL | passbolt-db | passbolt | — | 500 MiB | passbolt-backups 2G |
| PostgreSQL | shlink-db | shlink | — | 5 GiB | — |
| MySQL | mysql-0 | gibbon | — | 20 GiB | — |
| Redis | redis-0 | apptolast-invernadero-api | — | 500 MiB | — |
| Redis | redis-0 | apptolast-menus-dev | — | 2 GiB | — |
| Redis | redis-coordinator | n8n | — | — (sin PVC) | — |
| EMQX | emqx-0 | apptolast-invernadero-api | — | 5 GiB | — |
| etcd | etcd-apptolastserver | kube-system | (static pod) | en `/var/lib/etcd` | snapshot cron en `/etc/cron.d/etcd-snapshot` |

→ **Sólo n8n y passbolt tienen PVC de backup**. El resto vive sin backup automatizado visible. Dolor P0 #3 del dossier.

## 7. Observabilidad — estado real

### Lo que SÍ corre

| Componente | Namespace | URL |
|---|---|---|
| kube-state-metrics | monitoring | (interno) |
| metrics-server | kube-system | (interno, hace `kubectl top` funcionar) |
| Dozzle (logs live) | monitoring | https://dozzle.apptolast.com / https://logs.apptolast.com |
| Uptime Kuma | cluster-ops | https://kuma.apptolast.com |
| Homepage (dashboard de paneles) | cluster-ops | https://dashboard.apptolast.com |
| Event-exporter (K8s events → Discord) | cluster-ops | — |
| Status page | cluster-ops | https://status.apptolast.com |

### Lo que NO corre pero su huella está

Certs huérfanos en cert-manager (emitidos pero sin IngressRoute en uso):
- `monitoring/alertmanager-dot-apptolast-com-tls`
- `monitoring/grafana-dot-apptolast-com-tls`
- `monitoring/prometheus-dot-apptolast-com-tls`
- `monitoring/logs-tls`
- `langflow/generator-ui-tls`, `llm-router-tls`, `rag-service-tls`
- `redisinsight/kali-apptolast-com-tls`

→ Dolor P1 #7: eliminar en Fase 6.

### Alertas

- Discord webhooks: secret `cluster-ops/discord-webhooks` (4 webhooks distintos)
- Healthchecks.io: secret `cluster-ops/healthchecks-io`
- Telegram bot: secret `cluster-ops/telegram-bot`
- Kuma push tokens: secret `cluster-ops/kuma-push-tokens` (8 push monitors)

## 8. cluster-ops — sistema autónomo existente

18 CronJobs en namespace `cluster-ops`:

| Nombre | Schedule | Función inferida |
|---|---|---|
| cert-checks | `0 */6 * * *` | Chequea expiración certs cada 6h |
| **cluster-healthcheck-discord** | `0 9 * * *` | ⚠️ **SUSPENDED** (mando diario a Discord) |
| cluster-self-healing | `*/30 * * * *` | Auto-reparación pods Failed |
| emqx-checks | `*/5 * * * *` | Health EMQX |
| event-exporter-image-updater | `30 */12 * * *` | Actualiza imagen exporter |
| heartbeat-08utc | `0 8 * * *` | Healthchecks.io ping diario |
| host-checks | `*/5 * * * *` | Disk/mem/swap/load del host |
| infra-version-watch | `0 9 * * *` | Avisa nuevas versiones K8s/Longhorn |
| kuma-image-updater | `0 */12 * * *` | Update Uptime Kuma |
| latest-images-rotator | `30 3 * * *` | Rota imágenes :latest (anti drift) |
| log-hygiene | `0 4 * * 0` | Limpia logs antiguos (semanal) |
| longhorn-checks | `*/10 * * * *` | Health volúmenes |
| pg-metadata-checks | `*/5 * * * *` | Health postgres-metadata |
| redis-checks | `*/5 * * * *` | Health redis |
| tier0-image-latest-watch | `13 * * * *` | Watch imágenes tier0 |
| tier0-traffic-sentinel | `0 * * * *` | Detección tráfico anómalo |
| timescale-checks | `*/10 * * * *` | Health TimescaleDB |
| wireguard-checks | `*/5 * * * *` | Health WG (handshake stale, peers down) |

Documentación existente en `/home/admin/cluster-ops/audit/` (fuera de este repo):
- 27 RUNBOOKS RB-01..RB-27 + 6 specials
- AUTONOMOUS_AGENT_ARCHITECTURE.md, AUTONOMOUS_SYSTEM_DESIGN.md
- BACKUP_POLICY.md, RETENTION_POLICY.md, UPDATE_AUTOMATION_POLICY.md
- baseline/ (snapshot 2026-05-03)
- findings/, manifests/, changelog/, sessions/

→ Estos 27 runbooks se migran a `docs/runbooks/` en Fase 3 (4 por ejecución de la routine `runbook-migrator`).

## 9. Cron y scripts del host

### Crontab admin

```cron
0 */4  * * *  /home/admin/companies/apptolast/matrix-cubepath/scripts/k8s-doctor/diagnose.sh
0 */12 * * *  /home/admin/companies/apptolast/matrix-cubepath/scripts/k8s-doctor/remediate.sh
0 8    * * *  /home/admin/check-ssl-apptolast.sh
```

### `/etc/cron.d/`

- `etcd-snapshot` (snapshots etcd via `etcdctl`)
- `k8s-disk-cleanup`
- `weekly-disk-cleanup`

### Systemd timers no estándar

- `kubeadm-cert-renew.timer` next: `2026-07-01 03:13` (renovación trimestral cert control plane)
- `openclaw-check-models.timer` next: `2026-05-18 09:30` (verifica disponibilidad de modelos LLM)

## 10. Repositorios AppToLast GitHub

48 repos visibles. Más relevantes para el IDP:

**Desplegados actualmente**:
- `apptolast/GreenhouseAdmin` (Kotlin) → https://greenhouseadmin.apptolast.com ★ **referencia visual del frontend**
- `apptolast/InvernaderosAPI` (Kotlin) → invernadero-api dev/prod
- `apptolast/WhoopDavidAPI` (Kotlin) → david-whoop.apptolast.com
- `apptolast/menus-backend` (Kotlin) → menus-api-dev.apptolast.com
- `apptolast/InnemSellarBackend` (Rust) → inemsellarapi.apptolast.com
- `apptolast/tenacitOS` (TypeScript) → control.apptolast.com
- `apptolast/OpenClawAppToLast` (Shell, privado) → openclaw.*
- `apptolast/sistema-central-admin-servidor` (Kotlin) → **este repo**

**Plataforma/infra**:
- `apptolast/matrix-cubepath` (TypeScript) → SPA "Personal management" + scripts k8s-doctor (cron 4h/12h)
- `apptolast/apptolast-website` (TypeScript) → SPA principal de empresa

## 11. Dolores P0-P2 documentados (17 total)

### P0 — Riesgos de continuidad

1. **No hay backup off-site Longhorn** (backup-target vacío)
2. **Single-node** (default-replica-count=1) — reboot = downtime total
3. **Postgres backups inconsistentes** — sólo n8n y passbolt tienen PVC
4. **Helm release n8n-prod `failed`** desde 2025-12-22
5. **Disco raíz 73% con FreeDiskSpaceFailed continuo** — 48G containerd no-GCable
6. **Password Postgres n8n en env plaintext** (visible vía `kubectl describe pod`)
7. **NodePorts de DBs expuestos a internet público** (sin firewall verificado)

### P1 — Fricción operativa

8. **Observabilidad asimétrica** (Dozzle + Kuma + kube-state-metrics, sin time-series Prometheus/Grafana — los certs existen pero los pods no)
9. **WG corriendo como pod con `:latest`** (sin pin; PVC sin backup)
10. **Certs huérfanos cert-manager** (prometheus.*, grafana.*, alertmanager.*, kali.*, generator-ui.*, llm-router.*, rag-service.*)
11. **Hetzner Backups disabled** en panel
12. **Restos de RKE2** en cluster fleet-default (cronjob `rke2-machineconfig-cleanup`)

### P2 — Deuda de conocimiento

13. **hcloud CLI/token no instalado** en el servidor — no automatizable consulta Hetzner
14. **18 cronjobs cluster-ops** sin mapa rápido qué→cuándo→dónde-alerta (hay runbooks pero opacos para nuevo admin)
15. **Apps en namespaces vacíos** `apptolast` y `apptolast-greenhouse-admin-prod` (residuos o reservados?)
16. **n8n-backup/** en /home/admin (29G de /home) — sin política rotación visible
17. **PVs huérfanos local-storage** sin claim (`emqx-pv`, `postgresql-metadata-pv`, `redis-pv`, `timescaledb-pv`)

## 12. Lo que NO se pudo verificar (pendientes)

- Specs exactas Hetzner (tipo VPS confirmado CPX62 según panel; datacenter Falkenstein confirmado; vSwitch n/d)
- Inventario completo Cloudflare zonas/registros (sin token CF accesible desde servidor)
- Listado de nombres de workflows n8n (query SQL falló con escape jsonb; ejecuciones 7d: 3284 success, 117 cancel, 7 error, 59 rerun-success, 1 crash → salud 99.6%)
- Estado completo Rancher (clusters, users, RBAC, Fleet GitRepos) — sin rancher CLI
- Imágenes containerd (qué pesa qué) — `crictl images` requiere namespace `k8s.io`
- Network policies Calico — no consultadas en esta pasada

## 13. Cómo se cita este archivo

En markdown:
```
[source: docs/infrastructure/cluster-baseline-2026-05-13.md#7-observabilidad-estado-real@<sha>]
```

Donde `<sha>` se reemplaza por el hash del commit donde este doc se mergeó. Hasta entonces, usar `@HEAD` o el SHA del PR draft.

Para citas live (Fase 3+, cuando rag-ingestor exista):
```
[source: live:inventory@2026-05-13T21:10:00Z]
```

## 14. Refresh policy

Este snapshot es válido para citas hasta:
- **last-verified + 30 días** → routine `nightly-arch-review` empezará a alertar P2
- **last-verified + 90 días** → routine `citation-validator-sweep` abrirá issue STALE

Refresh recomendado: cada Fase mergea una versión actualizada (e.g., `cluster-baseline-2026-XX-YY.md`) con un nuevo dossier capturado en una sesión similar a la del 2026-05-12.
