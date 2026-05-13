---
title: "n8n-prod Helm release en estado `failed` — rollback a última revisión deployed"
type: runbook
owner: pablo
source-of-truth: "helm history n8n-prod -n n8n"
last-verified: 2026-05-13
tags: [n8n, helm, rollback, P0]
severity: P0
trigger:
  - manual: revision `helm list -A` muestra n8n-prod STATUS=failed
audience: [oncall]
applies-to:
  release: n8n-prod
  namespace: n8n
  chart: n8n 2.0.1 (appVersion 1.122.4)
---

# RB — n8n-prod Helm release failed → rollback

## 1 Síntoma

`helm list -A` muestra:

```
NAME       NAMESPACE  REVISION  UPDATED                STATUS  CHART     APP VERSION
n8n-prod   n8n        12        2025-12-22 23:40...    failed  n8n-2.0.1 1.122.4
```

El pod `n8n-prod-...` está `Running` y el servicio funciona, pero **cualquier `helm upgrade` futuro choca** porque la última revisión está en estado fallido.

[source: cluster-ops/audit/REPORT_2026-05-03.md#helm@HEAD]

## 2 Pre-checks (read-only)

```bash
helm history n8n-prod -n n8n
helm status n8n-prod -n n8n
kubectl -n n8n get pods,statefulsets,deployments
kubectl -n n8n get pvc                               # confirmar n8n-prod PVC bound
kubectl -n n8n logs deployment/n8n-prod --tail 100   # último estado conocido
```

Anota en tu sesión:
- **Revisión actual** (la `failed`): suele ser la más alta.
- **Última revisión `deployed`**: target del rollback.

## 3 Determinar revisión target

```bash
helm history n8n-prod -n n8n -o json \
  | jq '[.[] | select(.status == "deployed")] | last | .revision'
```

Ejemplo de output: `11`. Esta es la revisión target.

## 4 Backup defensivo antes de tocar

```bash
# Snapshot del PVC (Longhorn manual snapshot)
kubectl -n longhorn-system create -f - <<EOF
apiVersion: longhorn.io/v1beta2
kind: Snapshot
metadata:
  generateName: n8n-prod-pre-rollback-
  namespace: longhorn-system
spec:
  volume: $(kubectl -n n8n get pvc n8n-prod -o jsonpath='{.spec.volumeName}')
EOF

# Backup pg de la metadata n8n
kubectl -n n8n exec postgres-n8n-0 -- bash -lc \
  'PGPASSWORD=$POSTGRES_PASSWORD pg_dump -U $POSTGRES_USER -d $POSTGRES_DB --format=custom' \
  > /home/admin/backups/n8n-prerollback-$(date -u +%Y%m%dT%H%M%SZ).dump
ls -lh /home/admin/backups/n8n-prerollback-*.dump
```

## 5 Rollback

```bash
TARGET_REV=$(helm history n8n-prod -n n8n -o json \
  | jq '[.[] | select(.status == "deployed")] | last | .revision')

echo "Rollback a revision=$TARGET_REV"
helm rollback n8n-prod "$TARGET_REV" -n n8n --wait --timeout 5m
```

`--wait` hace que helm espere hasta que todos los recursos estén Ready (o falle). Si tarda >5 min, kubectl manual cleanup:

```bash
helm status n8n-prod -n n8n
kubectl -n n8n get pods -w
```

## 6 Verificación post

```bash
helm list -A | grep n8n-prod                          # STATUS=deployed
kubectl -n n8n rollout status deployment/n8n-prod
curl -fsS https://n8n.apptolast.com/healthz           # 200 OK
kubectl -n n8n exec postgres-n8n-0 -- bash -lc \
  'PGPASSWORD=$POSTGRES_PASSWORD psql -U $POSTGRES_USER -d $POSTGRES_DB -tAc \
   "SELECT count(*) FROM execution_entity WHERE \"startedAt\" > now() - interval '\''1 hour'\''"'
```

El último query debe seguir creciendo si los workflows triggered se ejecutan.

## 7 Rollback del rollback (si algo rompe)

```bash
helm history n8n-prod -n n8n              # ver nuevas revisiones tras rollback
helm rollback n8n-prod <revisión_previa> -n n8n
```

Si la DB se corrompió:

```bash
# Restaurar desde el dump del paso 4
kubectl -n n8n exec -i postgres-n8n-0 -- bash -lc \
  'PGPASSWORD=$POSTGRES_PASSWORD pg_restore -U $POSTGRES_USER -d $POSTGRES_DB --clean --if-exists' \
  < /home/admin/backups/n8n-prerollback-XXXXXXXX.dump
```

## 8 Citación

- Estado verificado 2026-05-12: `n8n-prod` revisión 12 STATUS=failed [source: cluster-ops/audit/REPORT_2026-05-03.md#helm@HEAD]
- 3284 ejecuciones triggered últimos 7d, 99.6% success [source: dossier 2026-05-12 §9 n8n executions@HEAD]
- Postgres en `postgres-n8n-0` v16.10 con PVC longhorn 10 GiB [source: cluster-ops/audit/baseline/postgres_inventory.txt@HEAD]
