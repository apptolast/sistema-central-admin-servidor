---
title: "Network Policies — defensa en profundidad single-node"
owner: pablo
type: security-policy
status: ready
phase: 6
last-verified: 2026-05-13
source-of-truth: k8s/hardening/networkpolicies/
tags: [networkpolicy, calico, security, p0, wave-f]
related-runbooks:
  - RB-50_LONGHORN_OFFSITE_BACKUP
  - RB-51_POSTGRES_PGDUMP
related-audits:
  - cluster-ops/audit/NODEPORT_EXPOSURE_AUDIT_2026-05-05.md
---

# Network Policies — defensa en profundidad

Política de aislamiento de red intra-cluster. Default = deny por namespace
sensible; whitelist explícita de pares (source pod → destination pod) por
necesidad operativa.

## Pre-condiciones

- **CNI**: Calico (confirmado vivo en `kube-system`, `kubectl get pods -n kube-system -l k8s-app=calico-node`).
- **Namespaces protegidos**: empezamos por `apptolast-invernadero-api` (P0
  según auditoría `NODEPORT_EXPOSURE_AUDIT_2026-05-05.md`). En Wave-F
  ampliamos a `n8n`, `keycloak`, `identity`, `platform`.

## Tabla de pares allowed (matriz actual)

| Source namespace/pod | Destination | Puerto | Razón |
|----------------------|-------------|--------|-------|
| `traefik/*` (label app.kubernetes.io/name=traefik) | `platform-app/*` | 8080 | Ingress HTTP → API monolito |
| `traefik/*` | `keycloak/*` | 8080 | Auth flow |
| `platform-app/*` | `postgres-platform/*` | 5432 | JPA pool |
| `platform-app/*` | `nats-jetstream/*` | 4222 | Event bus |
| `platform-app/*` | `rag-query/*` | 8082 | RAG calls (knowledge module) |
| `cluster-watcher/*` | `nats-jetstream/*` | 4222 | Event publisher |
| `cluster-watcher/*` | `kube-apiserver` | 443 | List/Watch K8s API |
| `rag-ingestor/*` | `pgvector-platform/*` | 5432 | Write embeddings |
| `rag-ingestor/*` | `nats-jetstream/*` | 4222 | Listen `docs.changed` |
| `rag-query/*` | `pgvector-platform/*` | 5432 | Read embeddings |
| `apptolast-invernadero-api/*` (label app) | `timescaledb` + `emqx` + `redis` | 5432/1883/6379 | App stack interno |

Cualquier pod sin coincidir con la matriz → tráfico denegado por la
default-deny base.

## Manifests aplicados

```
k8s/hardening/networkpolicies/
├── 00-default-deny-apptolast-invernadero-api.yaml  (P0 — invernadero-api)
└── 01-allow-app-to-db.yaml                          (P0 — invernadero-api app→DB)
```

Pendiente Wave-F D8 (no en este PR):
- `02-default-deny-platform.yaml`
- `03-allow-traefik-to-platform.yaml`
- `04-allow-platform-egress.yaml`
- `05-default-deny-keycloak.yaml`

## Apply procedure

```bash
# 1. Snapshot estado actual
kubectl get networkpolicy --all-namespaces -o yaml > /tmp/np-before.yaml

# 2. Dry-run server-side
kubectl apply -f k8s/hardening/networkpolicies/ --dry-run=server

# 3. Apply 1 por 1 con verificación entre cada uno
kubectl apply -f k8s/hardening/networkpolicies/00-default-deny-apptolast-invernadero-api.yaml
# Esperar 30s, verificar que las apps siguen funcionando:
kubectl -n apptolast-invernadero-api get pods   # todos Running
kubectl -n apptolast-invernadero-api logs -l app=invernadero-api --tail=20 | grep -i "connect"
# Si ves "connection refused" → rollback INMEDIATO:
#   kubectl -n apptolast-invernadero-api delete networkpolicy <name>

# 4. Después de validar el primer namespace, repetir para los siguientes
kubectl apply -f k8s/hardening/networkpolicies/01-allow-app-to-db.yaml
```

## Procedimiento de rollback

Si después de aplicar una NetworkPolicy las apps de ese namespace pierden
conectividad y NO se recupera en 60s:

```bash
# Eliminar la política recién aplicada
kubectl -n <namespace> delete networkpolicy <name>

# Verificar que la app recupera
kubectl -n <namespace> get pods -w
```

NetworkPolicy es declarativa — eliminar el objeto restaura el comportamiento
default (deny-none por defecto en Calico, all-allow). El daño es reversible.

## Cómo verificar que las políticas están aplicadas

```bash
# Listar todas las policies del cluster
kubectl get networkpolicy --all-namespaces -o wide

# Verificar que un pod específico está cubierto
kubectl -n apptolast-invernadero-api describe pod invernadero-api-0 | grep -i policy

# Test conectividad real con netshoot
kubectl run -it --rm netshoot --image=nicolaka/netshoot -n <ns> -- nc -zv <target-service> <port>
```

## Antipatterns a evitar

- ❌ NetworkPolicy sin pre-flight `--dry-run=server` y sin snapshot
- ❌ Aplicar a un namespace productivo sin haberlo probado en `cluster-ops`
- ❌ Usar `podSelector: {}` + `policyTypes: [Ingress]` SIN ninguna `ingress: []` regla — eso bloquea TODO incluido los probes K8s
- ❌ Bloquear egress a DNS (`kube-dns` o `coredns` en namespace `kube-system`) — rompe resolución
- ❌ Olvidar que Traefik vive en namespace `traefik` y necesita egress hacia tu app

## Citación

- Auditoría source: [source: cluster-ops/audit/NODEPORT_EXPOSURE_AUDIT_2026-05-05.md@HEAD]
- Manifests: [source: k8s/hardening/networkpolicies/@HEAD]
- Calico docs (vigente cluster): https://docs.tigera.io/calico/3.30/network-policy
- K8s NetworkPolicy semantics: https://kubernetes.io/docs/concepts/services-networking/network-policies/
