---
title: k8s/hardening — manifiestos defensivos para los dolores P0/P1
last-verified: 2026-05-13
owner: pablo
audience: [oncall, ops]
status: draft
---

# k8s/hardening

Estos manifiestos cubren los dolores **P0/P1** del dossier `cluster-ops/audit/` del cluster Hetzner CPX62 single-node (snapshot 2026-05-12).

> **CRITICAL**: NO se aplican automáticamente. Cada archivo debe revisarse + aplicarse con `kubectl apply -f --dry-run=server` antes de `--dry-run=none`. Algunos cambios pueden romper clientes externos (NodePorts) — leer el runbook asociado primero.

## Mapeo dolor → archivo + runbook

| ID | Dolor | Severidad | Archivo | Runbook |
|----|-------|-----------|---------|---------|
| P0-1 | NodePorts DB/Redis sin NetworkPolicy ni firewall validado externo | P0 | `networkpolicies/00-default-deny-apptolast-invernadero-api.yaml`, `networkpolicies/01-allow-app-to-db.yaml` | `cluster-ops/audit/NODEPORT_EXPOSURE_AUDIT_2026-05-05.md` |
| P0-2 | Longhorn sin backup remoto (single replica, single node = SPOF) | P0 | `backup/longhorn-backup-target-hetzner-storage-box.yaml` | `cluster-ops/audit/RUNBOOKS/LONGHORN_BACKUP_TARGET.md` |
| P0-3 | Sin pg_dump cross-DB para 7 Postgres + MySQL `gibbon` | P0 | `cronjobs-pgdump/cronjob-pgdump-template.yaml` + `cronjobs-pgdump/cronjob-pgdump-instances.yaml` | (nuevo) `docs/runbooks/PGDUMP_RESTORE.md` (Fase 6) |
| P1-1 | `n8n-prod` Helm release status `failed` | P1 | — (procedural) | `cluster-ops/audit/RUNBOOKS/RB-25_routine_failed.md` |
| P1-2 | Disco raíz 73% (48 G en `/var/lib/containerd`) | P1 | — (procedural) | `cluster-ops/audit/RUNBOOKS/RB-01_HOST_DISK_HIGH.md` |
| P1-3 | Certs huérfanos prometheus/grafana/alertmanager en cert-manager | P1 | — (procedural) | `cluster-ops/audit/RUNBOOKS/RB-18_CERT_EXPIRY.md` |
| P1-4 | n8n env con password en plaintext (no Secret) | P1 | (futuro) `secrets/n8n-env-to-secret.yaml` | `cluster-ops/audit/RUNBOOKS/DISCORD_WEBHOOK_SECRET.md` (patrón) |

## Pre-flight obligatorio

```bash
# 1. Verificar que el CNI aplica NetworkPolicy (Calico SÍ — confirmado por dossier)
kubectl get pods -n kube-system -l k8s-app=calico-node
# Output esperado: pod calico-node-* Running

# 2. Snapshot del estado actual antes de aplicar
kubectl get networkpolicy --all-namespaces -o yaml > /tmp/np-before.yaml
kubectl get svc --all-namespaces -o wide > /tmp/svc-before.txt

# 3. Dry-run server-side de TODO el directorio
kubectl apply -f k8s/hardening/networkpolicies/ --dry-run=server
```

## Citación

- NodePort inventory: [source: cluster-ops/audit/NODEPORT_EXPOSURE_AUDIT_2026-05-05.md#services-nodeport-relevantes@HEAD]
- Longhorn replica=1 + sin backup: [source: cluster-ops/audit/LONGHORN_BACKUP_AND_REPLICA_AUDIT_2026-05-05.md#findings@HEAD]
- Disco 73%: [source: cluster-ops/audit/REPORT_2026-05-03.md#estado-actual@HEAD]
