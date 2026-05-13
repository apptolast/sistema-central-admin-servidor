---
title: "Login Keycloak — pantalla de autenticación OIDC"
type: design-spec
owner: pablo
phase: 4
last-verified: 2026-05-13
status: draft
related-modules:
  - identity
source-of-truth: "https://keycloak.apptolast.com/realms/apptolast/.well-known/openid-configuration"
tags:
  - frontend
  - auth
  - keycloak
  - oidc
depends-on:
  - module:identity
  - service:keycloak
related-runbooks:
  - DISCORD_WEBHOOK_SECRET
see-also:
  - ../../../ARCHITECTURE.md
---

# Login Keycloak

> **Fase 4 dependency** — Esta pantalla se activa en Fase 4 (semanas 13-16) cuando se cablea el módulo `identity` con Keycloak. Ver [`ARCHITECTURE.md §4`](../../../ARCHITECTURE.md#4--roadmap-por-fases--8-fases-22-semanas). Hasta entonces, la app corre sin autenticación con un usuario stub `admin`.

## User story

Como cualquier usuario del IDP, quiero un login claro y rápido que delegue en Keycloak (OIDC) para no tener que gestionar otra contraseña, recordando mi rol (`admin` / `operator` / `viewer`) en cada sesión `[source: ARCHITECTURE.md §3 módulo identity]`.

## Componentes

- **Pantalla full-screen** previa al resto de la app (gate de autenticación).
- **Tarjeta central** (max-width 420dp, centrada vertical + horizontal):
  - Logo AppToLast (SVG monocromo neon `#00E676`, 64dp).
  - Título "Sistema Central".
  - Subtítulo "Internal Developer Platform · AppToLast".
  - Botón principal "Continuar con Keycloak" — full-width, primary, icono escudo.
  - Texto secundario debajo "Te redirigiremos a keycloak.apptolast.com".
  - Selector de idioma compacto abajo: `ES` / `EN` (default ES, persistido en localStorage).
- **Footer**:
  - Texto pequeño: "Build {gitSha} · Fase 4".
  - Link "Estado del cluster" (link público a Kuma `https://uptime.apptolast.com`).
- **Background**:
  - Gradiente sutil radial desde `#0F1419` (centro) a `#080A0C` (esquinas).
  - Opcionalmente: malla de puntos neon muy tenue (estética cluster).
- **Post-callback (no es pantalla, es flujo)**:
  - El callback OIDC entra a `/auth/callback?code=...`.
  - Backend valida con Keycloak, intercambia code por tokens, guarda `access_token` y `refresh_token` en **cookie httpOnly + Secure + SameSite=Lax**, redirige a `/`.
  - En `/`, el top-bar muestra el avatar del usuario (`InitialAvatar` heredado de GreenhouseAdmin) + chip `StatusChip` con su rol (`admin` verde, `operator` ámbar, `viewer` neutro).

## State / Props

- **ViewModel**: `LoginViewModel(identityClient: IdentityClient, locale: LocaleManager)`.
- **Flows**:
  - `state: StateFlow<LoginState>` — `Idle | Redirecting | CallbackProcessing | Error`.
  - `locale: StateFlow<Locale>` — `ES | EN`.
- **Trigger login**:
  - Click en "Continuar con Keycloak" → `window.location = ${keycloakUrl}/realms/apptolast/protocol/openid-connect/auth?client_id=sistema-central&redirect_uri=${origin}/auth/callback&response_type=code&scope=openid+profile+email&state=${csrfToken}`.
  - `csrfToken` se genera en frontend y se guarda en sessionStorage para validar al volver.
- **Callback**:
  - Pantalla intermedia minimal "Estableciendo sesión…" con spinner mientras el backend procesa.

## Layout responsive

| Breakpoint | Layout |
|---|---|
| Compact (<600dp) | Tarjeta full-width (margen 24dp lateral), logo y título arriba, botón full-width. Footer pegado al borde inferior. |
| Medium (600-840dp) | Tarjeta centrada 420dp con padding 32dp. Background gradient visible. |
| Expanded (>840dp) | Tarjeta centrada 420dp. Split visual: 60% background con identidad visual (puede incluir un screenshot blureado del dashboard detrás del gradient), 40% derecho ocupado por la tarjeta. |

## Theming

- Background: gradient radial `#0F1419` → `#080A0C`.
- Surface tarjeta: `#1A1E23`, sombra elevada (24dp), `RoundedCornerShape(8.dp)`.
- Primary botón: `#00E676`, texto `#0F1419`, hover `#00C766`.
- Texto título: `#FFFFFF`.
- Texto secundario: `#FFFFFF` alpha 0.60.
- Error banner: `#FF5252` alpha 0.16, texto `#FF5252`, borde 1dp.
- Selector idioma: chip outline `#00E676` alpha 0.4, activo fill.
- Tipografía:
  - display-small para "Sistema Central".
  - title-small para subtítulo.
  - label-large para botón.
  - body-small para texto auxiliar.

## Accesibilidad

- ARIA: `role=main` en la tarjeta, `aria-labelledby` apuntando al título "Sistema Central".
- Botón principal con `aria-label="Continuar con Keycloak. Te redirigirá al servicio de autenticación."`.
- Foco inicial en el botón principal al cargar (mejor UX teclado).
- Tab order: idioma ES → idioma EN → botón principal → link footer.
- Enter sobre el botón dispara el redirect.
- Banner de error con `role=alert` para anuncio inmediato del screen reader.
- Contraste WCAG AA garantizado (`#0F1419` sobre `#00E676` ratio 10.3:1; texto secundario alpha 0.60 sobre `#1A1E23` verificado).
- `prefers-reduced-motion` honrado: deshabilita la animación del gradient y del spinner sustituyéndolo por texto "Cargando…".

## Copy literal (español)

- Título: "Sistema Central"
- Subtítulo: "Internal Developer Platform · AppToLast"
- Botón principal: "Continuar con Keycloak"
- Texto auxiliar: "Te redirigiremos a keycloak.apptolast.com"
- Selector idioma: "ES" / "EN"
- Pantalla intermedia callback: "Estableciendo sesión…"
- Footer build: "Build {gitSha} · Fase 4"
- Footer link: "Estado del cluster"
- Error `invalid_grant`: "Tu sesión expiró o el código de autorización ya no es válido. Inicia sesión de nuevo."
- Error `server_error`: "Keycloak no respondió correctamente. Reintenta en unos segundos."
- Error `access_denied`: "Has cancelado el inicio de sesión. Inténtalo de nuevo cuando quieras."
- Error `state_mismatch`: "Detectamos un posible intento de CSRF. Por seguridad, vuelve a empezar."
- Error genérico: "No se pudo iniciar sesión. Si el problema persiste, contacta al admin del cluster."
- CTA error: "Reintentar"

## Copy literal (English, opcional)

- Title: "Sistema Central"
- Subtitle: "Internal Developer Platform · AppToLast"
- Primary button: "Continue with Keycloak"
- Auxiliary text: "You will be redirected to keycloak.apptolast.com"
- Callback intermediate: "Establishing session…"
- Error generic: "Sign-in failed. Please retry."

## Estados

- **Idle**: tarjeta con botón habilitado, sin banner de error.
- **Redirecting**: botón muestra spinner inline + texto "Redirigiendo…", click bloqueado.
- **CallbackProcessing**: pantalla intermedia minimal con spinner "Estableciendo sesión…" centrado.
- **Error invalid_grant**: tarjeta vuelve a mostrarse + banner rojo con copy correspondiente + CTA "Reintentar".
- **Error server_error**: igual con copy correspondiente; banner permite Reintentar.
- **Error access_denied**: banner ámbar (no rojo) + CTA "Reintentar".
- **Error state_mismatch**: banner rojo + texto sobre CSRF + CTA "Reintentar".
- **Success**: redirect inmediato a `/` (sin pantalla intermedia visible más allá del flash del callback).

## Cita la fuente

- Módulo de identidad: `identity` `[source: ARCHITECTURE.md §3]`.
- Auth tech: Keycloak 26.6 OIDC self-hosted en cluster `[source: ARCHITECTURE.md §2.2]`.
- Política de cookies: httpOnly + Secure + SameSite=Lax — decisión de seguridad documentada en `ADR` futuro (Fase 4).
- Roles `admin` / `operator` / `viewer` `[source: ARCHITECTURE.md §3 módulo identity]`.
- Fase activación: Fase 4 (semanas 13-16) `[source: ARCHITECTURE.md §4]`.

---

## Prompt para Claude Design

Copy-paste literal para https://claude.ai/design:

```
Estoy diseñando la pantalla de "Login OIDC con Keycloak" para un Internal
Developer Platform (IDP) llamado Sistema Central de AppToLast.

Tema: dark Material 3, OLED-optimizado, primary #00E676 (neon green),
background gradient radial #0F1419 → #080A0C, surface tarjeta #1A1E23,
RoundedCornerShape(8.dp). Stack: Compose Multiplatform Web (target wasmJs).

Layout: pantalla full-screen previa a la app (gate de autenticación).

Tarjeta central (max-width 420dp, centrada vertical + horizontal, sombra
elevada 24dp):
- Logo AppToLast en SVG monocromo verde neon #00E676, tamaño 64dp.
- Título "Sistema Central" (display-small).
- Subtítulo "Internal Developer Platform · AppToLast" (title-small).
- Botón principal full-width "Continuar con Keycloak" — fondo #00E676,
  texto #0F1419, icono escudo, hover #00C766.
- Texto auxiliar debajo: "Te redirigiremos a keycloak.apptolast.com"
  (body-small, color #FFFFFF alpha 0.60).
- Selector de idioma compacto abajo: chips "ES" / "EN" (ES default, outline
  con fill al activarse).

Footer minimal:
- Texto pequeño: "Build {gitSha} · Fase 4".
- Link "Estado del cluster" → https://uptime.apptolast.com.

Background: gradient radial desde el centro a las esquinas, opcionalmente
una malla muy tenue de puntos neon como acento. En expanded breakpoint, un
screenshot blureado del dashboard detrás del gradient en el 60% izquierdo,
con la tarjeta ocupando el 40% derecho.

Pantalla intermedia tras callback: spinner verde neon centrado con texto
"Estableciendo sesión…", duración esperada < 1 segundo.

Manejo de errores (todos como banner rojo dentro de la tarjeta, con CTA
"Reintentar"):
- invalid_grant: "Tu sesión expiró o el código de autorización ya no es
  válido. Inicia sesión de nuevo."
- server_error: "Keycloak no respondió correctamente. Reintenta en unos
  segundos."
- access_denied: banner ámbar (no rojo) "Has cancelado el inicio de sesión.
  Inténtalo de nuevo cuando quieras."
- state_mismatch (CSRF): "Detectamos un posible intento de CSRF. Por
  seguridad, vuelve a empezar."

Breakpoints:
- Compact (<600dp): tarjeta full-width con margen 24dp, footer pegado al
  borde inferior.
- Medium (600-840dp): tarjeta centrada 420dp con padding 32dp, gradient
  visible alrededor.
- Expanded (>840dp): split 60/40 con identidad visual a la izquierda y
  tarjeta a la derecha.

Accesibilidad: role=main, aria-labelledby al título, botón con aria-label
descriptivo del flujo, foco inicial en el botón, banner de error con
role=alert, contraste WCAG AA verificado, prefers-reduced-motion honrado.

Copy en español neutro (default). Genera mockups wireframe + high-fidelity
para los 3 breakpoints, incluyendo:
1. Estado idle de la pantalla.
2. Estado redirecting con spinner en el botón.
3. Estado error invalid_grant con banner rojo + CTA Reintentar.
```
