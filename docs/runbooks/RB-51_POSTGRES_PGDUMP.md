---
title: "RB-51 — Postgres pg_dump cross-DB (7 Postgres + 1 MySQL)"
owner: pablo
type: runbook
status: ready
phase: 6
severity: P0
last-verified: 2026-05-13
source-of-truth: k8s/hardening/cronjobs-pgdump/cronjob-pgdump-template.yaml
audience: [oncall, ops]
tags: [postgres, mysql, backup, pgdump, p0, wave-f]
depends-on:
  - namespace:n8n
  - service:postgres-n8n
related-audits:
  - cluster-ops/audit/LONGHORN_BACKUP_AND_REPLICA_AUDIT_2026-05-05.md
---

# RB-51 — Postgres pg_dump scheduled backups

**Severidad P0.** Longhorn backup (RB-50) cubre snapshots a nivel de bloque,
pero NO da consistencia transaccional para databases. Si un Postgres está
escribiendo cuando se snapshotea su PVC, el restore puede quedar en estado
inconsistente. Solución: `pg_dump` programado adicional al snapshot Longhorn.

## Inventario de databases (snapshot 2026-05-12)

| Namespace | Servicio | Tipo | DB principal | Backup hoy | Backup nuevo |
|-----------|----------|------|--------------|------------|--------------|
| n8n | postgres-n8n | Postgres 16 | n8n | NO | pg_dump diario 02:30 UTC |
| invernadero-api | timescaledb | TimescaleDB 2.17 | invernadero | NO | pg_dump_timescale diario |
| keycloak | postgres-keycloak | Postgres 16 | keycloak | NO | pg_dump diario |
| openclaw | openclaw-postgres | Postgres 14 | claw_main | NO | pg_dump diario |
| langflow | langflow-postgres | Postgres 16 | langflow | NO | pg_dump diario |
| outline | outline-postgres | Postgres 15 | outline | NO | pg_dump diario |
| n8n | n8n-redis | Redis | — | NO (RDB local) | snapshot Longhorn ok |
| gibbon | gibbon-mysql | MySQL 8 | gibbon | NO | mysqldump diario |

## Apply

```bash
# Pre-flight: el template existe en k8s/hardening/cronjobs-pgdump/.
# Cada DB tiene su CronJob propio en cronjob-pgdump-instances.yaml.

kubectl apply -f k8s/hardening/cronjobs-pgdump/cronjob-pgdump-template.yaml \
  --dry-run=server

kubectl apply -f k8s/hardening/cronjobs-pgdump/cronjob-pgdump-instances.yaml \
  --dry-run=server

# Si los dry-runs pasan:
kubectl apply -f k8s/hardening/cronjobs-pgdump/
```

## Verificar primera ejecución

```bash
# Esperar al schedule (02:30 UTC) o disparar manualmente:
kubectl -n n8n create job --from=cronjob/pgdump-n8n pgdump-n8n-manual-$(date +%s)

# Observar el pod del job
kubectl -n n8n get jobs --selector=cronjob=pgdump-n8n -o wide

# Ver logs del primer pg_dump
kubectl -n n8n logs -l job-name=pgdump-n8n-manual-XXXX --tail=100

# Confirmar dump subido al Storage Box
aws --endpoint-url=https://uXXXXX.your-storagebox.de:443 \
  s3 ls s3://pgdump-apptolast/n8n/$(date +%Y/%m/%d)/
```

## Estructura del dump en S3

```
s3://pgdump-apptolast/
  ├── n8n/2026/05/13/n8n-20260513-0230.sql.gz
  ├── keycloak/2026/05/13/keycloak-20260513-0231.sql.gz
  ├── langflow/2026/05/13/langflow-20260513-0232.sql.gz
  ├── outline/2026/05/13/outline-20260513-0233.sql.gz
  └── ...
```

Retención: 30 días (lifecycle policy en bucket).

## Test de restore (DR drill — mensual)

```bash
# 1. Descargar último dump
aws --endpoint-url=https://uXXXXX.your-storagebox.de:443 \
  s3 cp s3://pgdump-apptolast/n8n/2026/05/13/n8n-20260513-0230.sql.gz /tmp/

gunzip /tmp/n8n-20260513-0230.sql.gz

# 2. Crear DB efímera en un Postgres test (NO el productivo)
kubectl -n n8n exec -it postgres-n8n-0 -- psql -U postgres \
  -c "CREATE DATABASE n8n_restore_test;"

# 3. Restaurar
kubectl -n n8n exec -i postgres-n8n-0 -- psql -U postgres n8n_restore_test \
  < /tmp/n8n-20260513-0230.sql

# 4. Verificar tabla critical existe
kubectl -n n8n exec -it postgres-n8n-0 -- psql -U postgres n8n_restore_test \
  -c "SELECT count(*) FROM workflow_entity;"
# Esperado: count > 0

# 5. Cleanup
kubectl -n n8n exec -it postgres-n8n-0 -- psql -U postgres \
  -c "DROP DATABASE n8n_restore_test;"
```

## Troubleshooting

| Síntoma | Causa probable | Fix |
|---------|----------------|-----|
| Job `pgdump-X` exit 1 con "connection refused" | Service `postgres-X` no resolvible | Verificar `kubectl -n <ns> get svc` y FQDN en el CronJob |
| Job exit 1 con "password authentication failed" | Secret `pgdump-credentials` mal | Re-crear secret desde Passbolt |
| Dumps no aparecen en S3 | AWS credentials Storage Box mal | Verificar `AWS_ENDPOINTS` y reintentar manualmente |
| Dump muy grande (>1GB compressed) | Postgres con tabla bloat o gran historial | `VACUUM FULL` semanal + considerar `pg_dump --jobs=4` |

## Cómo verificar éxito del runbook

- [ ] CronJobs aplicados: `kubectl get cronjob --all-namespaces -l apptolast.com/hardening=pgdump`
- [ ] Primera ejecución manual exitosa para cada DB
- [ ] Dumps presentes en Storage Box bucket `pgdump-apptolast`
- [ ] DR drill restore ejecutado en al menos 1 DB

## Citación

- Auditoría: [source: cluster-ops/audit/LONGHORN_BACKUP_AND_REPLICA_AUDIT_2026-05-05.md@HEAD]
- Template CronJob: [source: k8s/hardening/cronjobs-pgdump/cronjob-pgdump-template.yaml@HEAD]
- Postgres docs: https://www.postgresql.org/docs/16/backup-dump.html
