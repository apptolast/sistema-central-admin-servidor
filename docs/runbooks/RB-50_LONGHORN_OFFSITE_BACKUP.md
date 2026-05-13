---
title: "RB-50 — Longhorn off-site backup (Hetzner Storage Box)"
owner: pablo
type: runbook
status: ready
phase: 6
severity: P0
last-verified: 2026-05-13
source-of-truth: k8s/hardening/backup/longhorn-backup-target-hetzner-storage-box.yaml
audience: [oncall, ops]
tags: [longhorn, backup, storage-box, hetzner, p0, wave-f]
depends-on:
  - namespace:longhorn-system
  - service:longhorn-backupstore
related-runbooks:
  - cert-manager-orphan-cleanup
related-audits:
  - cluster-ops/audit/LONGHORN_BACKUP_AND_REPLICA_AUDIT_2026-05-05.md
---

# RB-50 — Longhorn off-site backup to Hetzner Storage Box

**Severidad P0.** Hoy Longhorn corre con `replica=1` sobre **un solo nodo
físico**. Cualquier fallo de disco → pérdida total de PVCs (TimescaleDB, los
7 Postgres del cluster, EMQX, Redis, n8n state). Sin backup remoto, el RPO
real es ∞.

Este runbook configura un BackupTarget S3-compatible apuntando a Hetzner
Storage Box, habilita backups diarios programados, y verifica restore.

## Pre-requisitos (manual one-shot)

1. **Storage Box en Hetzner Cloud Console** (≈12€/mes plan 1TB).
   `https://console.hetzner.com/projects/<id>/storage`. Anotar host
   (`uXXXXX.your-storagebox.de`) y crear sub-account con S3 perms.

2. **Crear bucket** desde la Sub-account web UI: `longhorn-backup-apptolast`.

3. **Secret K8s con credenciales** (NUNCA en git):
   ```bash
   kubectl create secret generic longhorn-backup-credential \
     -n longhorn-system \
     --from-literal=AWS_ACCESS_KEY_ID="<sub-account-user>" \
     --from-literal=AWS_SECRET_ACCESS_KEY="<sub-account-password>" \
     --from-literal=AWS_ENDPOINTS="https://uXXXXX.your-storagebox.de:443"
   ```

## Apply

```bash
# Dry-run server-side (descubre conflictos sin tocar nada)
kubectl apply -f k8s/hardening/backup/longhorn-backup-target-hetzner-storage-box.yaml \
  --dry-run=server

# Aplicar de verdad
kubectl apply -f k8s/hardening/backup/longhorn-backup-target-hetzner-storage-box.yaml

# Verificar
kubectl -n longhorn-system get backuptarget default -o yaml
# Esperado: status.available=true, status.lastSyncedAt=<reciente>
```

## Test de backup

```bash
# 1. Listar volúmenes Longhorn
kubectl -n longhorn-system get volumes.longhorn.io -o wide

# 2. Disparar backup manual de un volumen test (NO production)
VOL=longhorn-test-pvc
kubectl annotate volume -n longhorn-system $VOL \
  longhorn.io/last-backup-request="$(date +%s)" --overwrite

# 3. Observar BackupVolume aparecer
kubectl -n longhorn-system get backupvolumes.longhorn.io

# 4. Listar backups en el Storage Box (vía Longhorn UI o S3 client)
aws --endpoint-url=https://uXXXXX.your-storagebox.de:443 \
  s3 ls s3://longhorn-backup-apptolast/backupstore/volumes/
```

## Test de restore (DR drill obligatorio cada 30 días)

```bash
# 1. Crear PVC nuevo desde un Backup
cat <<EOF | kubectl apply -f -
apiVersion: longhorn.io/v1beta2
kind: Volume
metadata:
  name: restore-test
  namespace: longhorn-system
spec:
  fromBackup: "s3://longhorn-backup-apptolast@hetzner-storagebox/?backup=backup-XXXX&volume=<source>"
  numberOfReplicas: 1
  size: "1Gi"
EOF

# 2. Comprobar que el restore se completa
kubectl -n longhorn-system get volume restore-test -w
# Esperado: state=attached, robustness=healthy

# 3. Cleanup
kubectl -n longhorn-system delete volume restore-test
```

## Backups recurrentes (programación)

Una vez verificado el target, programar `RecurringJob` para todos los volumes
con label `recurring-backup=enabled`:

```yaml
apiVersion: longhorn.io/v1beta2
kind: RecurringJob
metadata:
  name: daily-backup
  namespace: longhorn-system
spec:
  cron: "0 3 * * *"       # 3 AM UTC diariamente
  task: backup
  groups:
    - default
  retain: 14              # conservar 14 backups (2 semanas)
  concurrency: 2
  labels:
    apptolast.com/scheduled: "true"
```

Aplicar y verificar al día siguiente que el job corrió:

```bash
kubectl -n longhorn-system get recurringjob daily-backup -o yaml
kubectl -n longhorn-system get backupvolumes
```

## Troubleshooting

| Síntoma | Causa probable | Fix |
|---------|----------------|-----|
| `backuptarget` status `available: false` | Credenciales mal | Re-crear secret y `kubectl rollout restart deploy -n longhorn-system longhorn-manager` |
| `backup` queda en estado `error` con "connection refused" | Storage Box S3 endpoint mal formateado | Verificar `AWS_ENDPOINTS` incluye `https://` y puerto `:443` |
| Backups crecen sin límite | `retain` no aplicado o RecurringJob mal | Verificar el RecurringJob y `kubectl describe backupvolume <name>` |

## Cómo verificar éxito del runbook

- [ ] `kubectl -n longhorn-system get backuptarget default` → `available: true`
- [ ] Test manual de backup de un volume → aparece en Storage Box
- [ ] RecurringJob aplicado, primer backup auto a la mañana siguiente
- [ ] DR drill restore ejecutado y volumen montable

## Citación

- Auditoría source: [source: cluster-ops/audit/LONGHORN_BACKUP_AND_REPLICA_AUDIT_2026-05-05.md@HEAD]
- BackupTarget manifest: [source: k8s/hardening/backup/longhorn-backup-target-hetzner-storage-box.yaml@HEAD]
- Longhorn 1.7 backup docs: https://longhorn.io/docs/1.7.2/snapshots-and-backups/setup-backup-and-restore/
