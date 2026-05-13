---
title: Keycloak — OIDC IdP del IDP
owner: pablo
type: service
phase: 4
status: scaffold
last-verified: 2026-05-13
source-of-truth: k8s/helm/keycloak/
tags: [identity, oidc, keycloak, phase-4, wave-d]
depends-on:
  - namespace:identity
  - pvc:keycloak-data-longhorn
used-by:
  - service:idp-frontend
  - service:idp-backend
related-runbooks: []
related-adrs:
  - 0008-identity-oidc-spring-security
---

# Keycloak 26.6 — IdP OIDC del IDP

Phase 4 / Wave-D D1. Chart Helm minimal en `k8s/helm/keycloak/` que despliega
Keycloak 26.6 oficial con realm `apptolast` autoimportado.

## Status

**Scaffold listo, NO desplegado** en el cluster a fecha 2026-05-13. Este
documento describe el contrato. El deployment real es responsabilidad de
Wave-D D4 (Pablo / devops-engineer) en una sesión separada con acceso al
cluster vivo.

## Deploy

```bash
# 1. Crear el secret de admin antes del install (NO commitear values con
#    passwords plain — el chart NO los acepta, sólo lee del secret).
kubectl create namespace identity
kubectl -n identity create secret generic keycloak-admin-credentials \
  --from-literal=username=admin \
  --from-literal=password=$(openssl rand -base64 24)

# 2. Si se usa Postgres externo (recommended para prod):
kubectl -n identity create secret generic keycloak-postgres-credentials \
  --from-literal=user=keycloak \
  --from-literal=password=$(openssl rand -base64 24)
# y editar values.yaml: database.vendor=postgres

# 3. Install
helm -n identity install keycloak k8s/helm/keycloak/

# 4. Esperar a ready (60-90s primer arranque)
kubectl -n identity wait --for=condition=Available deploy/keycloak --timeout=180s
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

`start-dev` por default (Wave-D D1 scaffold). En production REAL se promueve
a `start` con SSL configurado externamente (Wave-D D8 hardening). Ver
`docs/runbooks/RB-XX-keycloak-prod-promote.md` (TBD).

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

- **D2** (este PR): Identity module Spring Security Resource Server
  consumiendo `idp-backend` client → validar JWTs.
- **D4**: integración real con Keycloak vivo. Frontend OIDC code flow.
- **D8** (Phase 4 hardening): promoción a `start` mode, TLS edge en
  Keycloak directamente (sin tilde de Traefik), session cookie Secure.

## Citas

- `k8s/helm/keycloak/` — chart (verificable con `helm lint k8s/helm/keycloak/`).
- `ARCHITECTURE.md §3` — listado de bounded contexts Phase 4.
- `docs/adrs/0001-spring-modulith-vs-microservices.md` — racional del IdP
  centralizado para single-tenant.
