---
title: "Deploy guide — qué desplegar y qué DNS configurar"
owner: pablo
type: operations
status: ready
last-verified: 2026-05-13
source-of-truth: k8s/helm/
audience: [ops, devops]
tags: [deploy, dns, ingress, traefik, cloudflare]
---

# Deploy guide — IDP de AppToLast en cluster vivo

Estado a 2026-05-13: las 5 imágenes Docker están publicadas en
`ghcr.io/apptolast/*:latest` (y mirror Docker Hub `apptolast/apptolast-*`).
Cada servicio tiene su Helm chart en `k8s/helm/`. Este doc dice **qué
desplegar** y **qué DNS records crear** para tener el IDP funcionando
end-to-end en `idp.apptolast.com`.

## TL;DR — DNS que Pablo necesita configurar en Cloudflare

| Hostname | Tipo | Apunta a | Proxy Cloudflare | Cuándo |
|----------|------|----------|------------------|--------|
| `idp.apptolast.com` | A | IP pública Hetzner CPX62 (`138.199.157.58`) | **Off** (DNS-only, `:grey-cloud:`) | Antes de helm install platform + frontend |
| `auth.apptolast.com` | A | IP pública Hetzner CPX62 (mismo IP) | **Off** (DNS-only, `:grey-cloud:`) | Antes de helm install keycloak (Phase 4 D4) |

**Por qué proxy Off:** cert-manager con `cloudflare-clusterissuer` (DNS-01
challenge) necesita resolver el TXT challenge directamente desde Let's
Encrypt — el proxy de Cloudflare interfiere con el flujo HTTP-01 si lo
usaramos. DNS-01 vía API key de Cloudflare es compatible con proxy Off.

No hace falta DNS para los servicios internos (cluster-watcher,
rag-ingestor, rag-query) — son `ClusterIP` only y se accede vía
service.namespace.svc dentro del cluster.

## Arquitectura de routing

**Un solo dominio público para el IDP:** `idp.apptolast.com`. Traefik
hace path-based routing:

```
                 idp.apptolast.com  (TLS via cert-manager + cloudflare DNS-01)
                          │
                          ├─ /api/*       ─► platform-app (Spring Modulith)
                          ├─ /actuator/*  ─► platform-app
                          ├─ /oauth2/*    ─► platform-app (OIDC client, Phase 4)
                          ├─ /login       ─► platform-app
                          └─ /            ─► frontend (Compose Wasm + nginx)

                 auth.apptolast.com  (TLS via cert-manager)
                          │
                          └─ /            ─► keycloak (OIDC IdP)
```

Same-origin para frontend ↔ backend evita CORS preflight y permite
que las cookies de sesión OIDC funcionen sin `SameSite=None`.

## Orden de despliegue (1ª vez)

### Paso 1 — DNS records

En `https://dash.cloudflare.com/<account>/apptolast.com/dns/records`:

```
A  idp.apptolast.com   138.199.157.58   Proxy: Off (DNS only)
A  auth.apptolast.com  138.199.157.58   Proxy: Off (DNS only)
```

TTL `Auto`. Esperar ~60s a propagación (`dig +short idp.apptolast.com`).

### Paso 2 — Postgres del platform-app

```bash
# Asumimos que ya hay un PostgreSQL desplegado en el cluster (en namespace
# n8n) para n8n. Reusar la instancia o crear `postgres-platform` con StatefulSet.
# Si reusar: crear DB `apptolast_platform` y user `platform`.

kubectl -n platform create namespace platform || true
kubectl -n platform create secret generic platform-postgres-credentials \
  --from-literal=url='jdbc:postgresql://postgres-platform:5432/apptolast_platform' \
  --from-literal=user='platform' \
  --from-literal=password=$(openssl rand -base64 24)
```

### Paso 3 — Helm install platform-app + frontend

```bash
helm -n platform install platform     k8s/helm/platform
helm -n platform install frontend     k8s/helm/frontend

# Esperar a que ambos pods estén Ready
kubectl -n platform wait --for=condition=Available deploy/platform deploy/frontend --timeout=300s

# Verificar
kubectl -n platform get pods,svc,ingressroute
curl -I https://idp.apptolast.com/actuator/health      # → 200
curl -I https://idp.apptolast.com/                     # → 200 (frontend index.html)
```

### Paso 4 — Keycloak (Phase 4 D4, opcional para arrancar)

Sin Keycloak el IDP arranca con `identity.oidc.enabled=false` (default) y
todas las rutas son `permitAll`. Para activar auth real:

```bash
# Pre-flight: crear secrets bootstrap (NUNCA en values.yaml).
kubectl create namespace identity
kubectl -n identity create secret generic keycloak-admin-credentials \
  --from-literal=username=admin \
  --from-literal=password=$(openssl rand -base64 24)

# Install
helm -n identity install keycloak k8s/helm/keycloak

# Esperar (Keycloak tarda 60-90s en arrancar por la migration de H2)
kubectl -n identity wait --for=condition=Available deploy/keycloak --timeout=300s

# Verificar
curl -I https://auth.apptolast.com/health/ready        # → 200

# Activar OIDC en platform-app:
kubectl -n platform set env deploy/platform \
  IDENTITY_OIDC_ENABLED=true \
  IDENTITY_OIDC_ISSUER_URI=https://auth.apptolast.com/realms/apptolast
kubectl -n platform rollout restart deploy/platform
```

Ver `docs/services/keycloak.md` para el detalle del realm import + clients.

### Paso 5 — Servicios internos (extracted microservices)

Estos no necesitan DNS — son ClusterIP only:

```bash
# Watcher del K8s API → produce eventos a inventory
helm -n platform install cluster-watcher k8s/helm/cluster-watcher

# RAG ingestor (CronJob */15 * * * *) — necesita git access + OpenAI API key
kubectl -n platform create secret generic rag-ingestor-secrets \
  --from-literal=git-token='ghp_…' \
  --from-literal=openai-api-key='sk-…'
helm -n platform install rag-ingestor k8s/helm/rag-ingestor

# RAG query (Spring Boot facade + R2R Python detrás) — usado por knowledge module
helm -n platform install rag-query k8s/helm/rag-query
```

## Verify end-to-end

```bash
# 1. Frontend Compose Wasm carga
curl -s https://idp.apptolast.com/ | /bin/grep -q '<div id="composeApp">' && echo OK

# 2. Backend health
curl -s https://idp.apptolast.com/actuator/health/liveness | /bin/grep -q '"status":"UP"' && echo OK

# 3. API funciona
curl -s https://idp.apptolast.com/api/v1/inventory/pods?namespace=n8n | head -1
# Esperado: array JSON con los pods de n8n

# 4. Frontend habla con backend (open browser console)
open https://idp.apptolast.com/
# Click en un pod → detail page → debe cargar via /api/v1/inventory/pods/{ns}/{name}
```

## Rollback

Cualquier helm release se rollback con:

```bash
helm -n <namespace> history <release>
helm -n <namespace> rollback <release> <revision>
```

Keel auto-update también puede rollback si el deploy nuevo falla la
readiness probe (Keel marca el deployment como bad y revierte).

## Cómo Pablo me lo dijo

> "que servicio tenemos que desplegar porqué te configuro las DNS?"
> (2026-05-13)

**Respuesta corta:** los 2 DNS records de arriba (`idp.apptolast.com` +
`auth.apptolast.com`) apuntando al IP del Hetzner CPX62. Después
`helm install` en este orden: platform → frontend → keycloak.
Los 3 servicios internos no necesitan DNS público.

## Citación

- Charts: [source: k8s/helm/@HEAD]
- IngressRoute path-routing: [source: k8s/helm/platform/templates/ingressroute.yaml#L11-L18@HEAD]
- Architecture (Traefik 3.3.6 ya desplegado): [source: ARCHITECTURE.md#2.2@HEAD]
- Cloudflare DNS-01 cluster issuer: configurado en `k8s/hardening/` (pre-existente)
