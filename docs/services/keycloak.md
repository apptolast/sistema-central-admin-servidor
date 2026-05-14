---
title: Keycloak — OIDC IdP del IDP
owner: pablo
type: service
phase: 4
status: deployed
last-verified: 2026-05-14
source-of-truth: k8s/helm/keycloak/
tags: [identity, oidc, keycloak, phase-4, wave-d]
depends-on:
  - namespace:platform
  - pvc:keycloak-data-longhorn
  - postgres-schema:apptolast_platform.keycloak
used-by:
  - service:idp-frontend
  - service:idp-backend
related-runbooks: []
related-adrs:
  - 0008-identity-oidc-spring-security
---

# Keycloak 26.6.1 — IdP OIDC del IDP

Phase 4 / Wave-D. Chart Helm en `k8s/helm/keycloak/` que despliega Keycloak
26.6.1 oficial con realm `apptolast` autoimportado.

## Status

Desplegado en el namespace `platform` detrás de Traefik:

- host público: `https://auth.apptolast.com`
- TLS: `Certificate/keycloak-tls` emitido por `cloudflare-clusterissuer`
- persistencia: Postgres compartido `apptolast_platform`, schema `keycloak`
- health interno: puerto de management `9000`

## Deploy

```bash
# 1. Crear el secret de admin antes del install (NO commitear values con
#    passwords plain — el chart NO los acepta, sólo lee del secret).
kubectl create namespace platform
kubectl -n platform create secret generic keycloak-admin-credentials \
  --from-literal=username=admin \
  --from-literal=password=$(openssl rand -base64 24)

# 2. Secret Postgres y schema dedicado.
kubectl -n platform create secret generic keycloak-postgres-credentials \
  --from-literal=user="$DB_USER" \
  --from-literal=password="$DB_PASSWORD"

# 3. Install
helm -n platform install keycloak k8s/helm/keycloak/

# 4. Esperar a ready (60-90s primer arranque)
kubectl -n platform wait --for=condition=Available deploy/keycloak --timeout=300s
```

## Realm `apptolast`

Definido en `templates/realm-configmap.yaml` con:

- **3 realm roles**: `admin` (control total), `operator` (trigger comandos no
  destructivos), `viewer` (read-only). Default role = `viewer`.
- **2 clients**:
  - `idp-frontend` (public, PKCE S256) — para el Compose Web app.
    Redirect URIs: `https://idp.apptolast.com/*` + `http://localhost:8080/*`.
  - `idp-backend` (confidential, bearer-only) — para validación de JWT en el
    platform-app (Spring Security Resource Server).
- **Brute force protection** activada: 5 fallos = 15 min lockout temporal.
- **Registration cerrado** (single-tenant, AppToLast solo).

## Modo de arranque

`start` con `KC_HTTP_ENABLED=true` porque TLS termina en Traefik. Health y
metrics quedan en el puerto interno de management `9000`; no se publican por
`auth.apptolast.com`.

## Resources / RAM

Reservado en el budget del cluster (ver `ARCHITECTURE.md §6`):

| Componente | RAM presupuestada |
|------------|-------------------|
| Keycloak + Postgres (compartido) | 1.2-1.5 GB |

`requests: 768Mi · limits: 1280Mi`. JVM con `KC_HEAP_SIZE` heredado de la
imagen oficial (auto-tuning por containers).

## Endpoints

- `https://auth.apptolast.com/realms/apptolast/.well-known/openid-configuration`
- `https://auth.apptolast.com/realms/apptolast/protocol/openid-connect/auth`
- `https://auth.apptolast.com/realms/apptolast/protocol/openid-connect/token`
- `https://auth.apptolast.com/realms/apptolast/protocol/openid-connect/certs`

## Próximas waves

- Integrar `platform-app` como OAuth2 login/client real contra este issuer.
- Cambiar la pantalla `Cuenta` para que el botón no sea placeholder y use el
  flujo `/oauth2/authorization/keycloak`.
- Endurecer sesiones/cookies y RBAC antes de cerrar rutas mutantes públicas.

## Citas

- `k8s/helm/keycloak/` — chart (verificable con `helm lint k8s/helm/keycloak/`).
- `ARCHITECTURE.md §3` — listado de bounded contexts Phase 4.
- `docs/adrs/0001-spring-modulith-vs-microservices.md` — racional del IdP
  centralizado para single-tenant.
