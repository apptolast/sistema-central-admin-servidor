---
title: "Wave D — Phase 4 Identity + Secrets prompts"
type: policy
owner: pablo
last-verified: 2026-05-13
audience: [phase-orchestrator-routine, oncall]
applies-to-wave: D (Phase 4 — Identity/Secrets)
precondition:
  - wave-C-merged (knowledge wired)
  - identity module skeleton + AuthorizationMatrix landed (commit f1bd858)
---

# Wave D — Phase 4 Identity/Secrets prompts

## D1 — Keycloak helm chart skeleton

**Prompt al devops-engineer:**

```
Crear k8s/helm/keycloak/ con Chart.yaml + values.yaml + templates/.

REQUISITOS (single-node minimal):
- imagen: quay.io/keycloak/keycloak:26.0
- Modo: production (KC_DB=postgres, KC_HOSTNAME=keycloak.apptolast.com)
- DB compartida con passbolt-db? NO — Keycloak quiere su propio PG. Crear
  postgres-keycloak StatefulSet 1Gi longhorn dentro del mismo chart.
- 1 réplica (single-node), no HA
- ENV obligatorias: KEYCLOAK_ADMIN, KEYCLOAK_ADMIN_PASSWORD (Secret)
- IngressRoute Traefik para keycloak.apptolast.com (añadir a Cloudflare DNS
  primero — instrucción manual al operador)
- Cert vía cloudflare-clusterissuer DNS01
- securityContext mínimo

NO desplegar — sólo helm lint + kubectl apply --dry-run=client.

Documentar en docs/services/keycloak.md.
```

## D2 — Identity module: wire Spring Security OIDC

**Prompt al backend-dev (owner: platform/identity):**

```
El módulo identity ya tiene Principal/Role/Permission domain + AuthorizationMatrix.
Añade:

1. infrastructure/security/SecurityConfig.kt:
   - @EnableWebSecurity @EnableMethodSecurity
   - SecurityFilterChain: oauth2 resource server con JwtAuthenticationConverter
     que mapea claim 'realm_access.roles' → Spring authorities ROLE_ADMIN, etc.
   - Public paths: /actuator/health/**
   - Todo lo demás: authenticated()

2. infrastructure/security/JwtRoleConverter.kt:
   - Convierte JWT claim 'realm_access.roles' (Keycloak default) en
     Set<Role> usando el AuthorizationMatrix existente

3. application/service/AuthorizationService.kt:
   - implements AuthorizationUseCase
   - require(principal, permission) — usa AuthorizationMatrix
   - has() / rolesGranting()

4. Tests:
   - JwtRoleConverter parsea correctamente un JWT mock con claim realm_access
   - AuthorizationService rechaza VIEWER intentando REGISTRY_WRITE (P0 destructivo)
   - 401 si JWT inválido, 403 si rol insuficiente

OWNERSHIP: platform/identity/**.
STACK: Spring Security 6.5 + spring-boot-starter-oauth2-resource-server (ya
en build.gradle.kts).

NO instales Keycloak ni configures realm — eso es D1+manual.
```

## D3 — Secrets module: integración con Passbolt

**Prompt al backend-dev (owner: platform/secrets):**

```
Passbolt corre en passbolt.apptolast.com con API REST + GPG E2E. Para que la
plataforma pueda leer secretos sin exponerlos en kubectl describe pod, queremos:

1. SecretStore port — interface con un solo método:
   fun get(key: SecretKey): SecretValue? — devuelve null si no existe.

2. PassboltSecretAdapter — implementa SecretStore haciendo:
   - GET https://passbolt.apptolast.com/users.json?contain[gpgkey]=1
   - POST /auth/login.json con GPG challenge (signed via JCA BouncyCastle)
   - GET /resources.json?filter[has-tag]=apptolast-platform
   - Decrypt response con GPG private key (mount como Secret volume)

3. CachingSecretStore — wrap PassboltSecretAdapter con cache TTL=5min para
   reducir GPG decrypts (que son caros).

4. Tests:
   - StubSecretStore en tests, no llamar Passbolt real
   - SecretKey value object con validation (anti-injection)
   - SecretValue es @JvmInline value class para no loggear nunca el contenido

5. NO exponer endpoints públicos — Secrets es módulo interno, usado por
   Identity (Keycloak admin password) + Automation (futuro: rotación de
   passwords).

OWNERSHIP: platform/secrets/**.
NO toques: passbolt deployment, identity module.
```

## Verificación end-of-wave-D

```bash
cd platform && ./gradlew :identity:test :secrets:test
helm lint k8s/helm/keycloak/
# Manual: añadir 'keycloak' a Cloudflare DNS apuntando a 138.199.157.58
```

## Citación

- Passbolt URL + ns: dossier 2026-05-12 §6 (passbolt.apptolast.com)
- Roles AuthorizationMatrix: platform/identity/src/main/kotlin/.../AuthorizationMatrix.kt
- Spring Security 6.5 docs: https://docs.spring.io/spring-security/reference/6.5/index.html
