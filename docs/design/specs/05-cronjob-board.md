---
title: "Cronjob Board — grid unificado de cronjobs (k8s + n8n + host)"
type: design-spec
owner: pablo
phase: 5
last-verified: 2026-05-13
status: draft
related-modules:
  - automation
  - observability
  - identity
source-of-truth: "kubectl -n cluster-ops get cronjobs + n8n API + crontab -l"
tags:
  - frontend
  - automation
  - cronjobs
depends-on:
  - module:automation
  - module:identity
related-runbooks: []
see-also:
  - ../../../ARCHITECTURE.md
---

# Cronjob Board

## User story

Como admin/operator, quiero ver en una sola pantalla los ~28 cronjobs activos del sistema (18 cluster-ops + ~7 workflows n8n + 3 cronjobs del host) `[source: dossier-2026-05-12]` con su próxima ejecución y último resultado, y poder disparar manualmente cualquiera sin acceder por SSH.

## Componentes

- **Top-bar**:
  - Search "Buscar cronjob…".
  - Filtros: chip "Todos / K8s / n8n / Host", dropdown namespace, dropdown estado ("Success" / "Failed" / "Never" / "Suspended").
  - Toggle "Ver suspendidos" (oculta por defecto los suspended, p. ej. `cluster-healthcheck-discord`).
- **Grid responsive de tarjetas** (auto-fit, mínimo 280dp por tarjeta):
  - **Header tarjeta**: icono sistema (k8s / n8n / host), nombre del cronjob, namespace o ruta.
  - **Body**:
    - Schedule cron parseado a humano: `*/5 * * * *` → "cada 5 min", `0 8 * * *` → "diario 08:00", `0 */4 * * *` → "cada 4 h".
    - Último estado con `StatusChip` (success verde / failed rojo / never gris / suspended ámbar).
    - "Última ejecución: hace {n} min" + "Próxima: en {m} min".
    - Mini barra horizontal con últimas 10 ejecuciones (cuadritos verdes/rojos tipo GitHub commits).
  - **Footer tarjeta**:
    - Botón "Ver histórico" (abre drawer derecho).
    - Botón "Ejecutar ahora" (admin only, oculto para `viewer`).
- **Drawer derecho de histórico**:
  - Header con nombre del cronjob.
  - Tabla últimas 50 ejecuciones: timestamp, duración, exit code / status, link a logs (Loki en Fase 2 / Dozzle de momento), botón "Ver YAML" (sólo k8s).
  - Gráfico mini de duración a lo largo del tiempo.

## Lista completa de cronjobs (28)

### Cluster-ops K8s (18) `[source: dossier-2026-05-12]`

- `cert-checks`
- `cluster-self-healing`
- `emqx-checks`
- `host-checks`
- `longhorn-checks`
- `pg-metadata-checks`
- `redis-checks`
- `timescale-checks`
- `wireguard-checks`
- `infra-version-watch`
- `latest-images-rotator`
- `log-hygiene`
- `tier0-image-latest-watch`
- `tier0-traffic-sentinel`
- `kuma-image-updater`
- `event-exporter-image-updater`
- `heartbeat-08utc`
- `cluster-healthcheck-discord` (SUSPENDED)

### n8n workflows activos (~7) `[source: dossier-2026-05-12]`

- Lista exacta se materializará al cablear el módulo `automation` con la API de n8n; placeholder UI para 7 cards.

### Host crontab (3) `[source: dossier-2026-05-12]`

- `k8s-doctor diagnose` — cada 4 h.
- `k8s-doctor remediate` — cada 12 h.
- `ssl-cert-check` — diario 08:00.

## State / Props

- **ViewModel**: `CronjobBoardViewModel(automationClient, identityClient)`.
- **Flows**:
  - `cronjobs: StateFlow<List<Cronjob>>` — agregado de `GET /api/v1/automation/cronjobs` (módulo `automation` une K8s + n8n + crontab host) `[source: ARCHITECTURE.md §3]`.
  - `history: StateFlow<List<Execution>>` — por cronjob, `GET /api/v1/automation/cronjobs/{id}/executions?limit=50`.
  - `userRole: StateFlow<Role>` — controla visibilidad "Ejecutar ahora".
  - `filters: MutableStateFlow<CronjobFilters>`.
- **Run-now action**:
  - `POST /api/v1/automation/cronjobs/{id}/trigger`.
  - Pide confirmación si el cronjob está marcado como `dangerous: true` en su declarativa (e.g., `cluster-self-healing`).

## Layout responsive

| Breakpoint | Layout |
|---|---|
| Compact (<600dp) | Grid 1 columna (tarjetas stack vertical). Filtros en bottom-sheet. Drawer histórico = pantalla completa. |
| Medium (600-840dp) | Grid 2 columnas. Filtros visibles arriba como chips horizontales scrollables. Drawer 420dp. |
| Expanded (>840dp) | Grid auto-fit (3-4 columnas según ancho). Filtros en barra superior persistente. Drawer 480dp. |

## Theming

- Background: `#0F1419`.
- Tarjeta surface: `#1A1E23`, hover eleva a `#212529`, `RoundedCornerShape(8.dp)`.
- Borde tarjeta: 1dp `#2A2F35`. Si el cronjob está suspended: borde ámbar `#FFB300`.
- Icono sistema:
  - k8s: hexágono `#326CE5` (azul k8s).
  - n8n: rosa `#EA4B71`.
  - host: gris `#9E9E9E`.
- Mini barra ejecuciones: cuadritos 8x8 con gap 2dp. Verde `#00E676` success, rojo `#FF5252` failed, gris `#37474F` skipped.
- Botón "Ejecutar ahora": fondo `#00E676`, texto `#0F1419`, ícono play.
- Botón "Ver histórico": outline `#00E676`.
- Chip suspended: fondo `#FFB300` alpha 0.16, texto `#FFB300`.

## Accesibilidad

- ARIA roles: `role=grid` con `role=gridcell` por tarjeta; `aria-rowcount` y `aria-colcount` dinámicos.
- Cada tarjeta `aria-label="Cronjob {name}, sistema {system}, último estado {status}, próxima ejecución {time}"`.
- Navegación por teclado: flechas mueven foco entre tarjetas, Enter activa "Ver histórico", Shift+Enter (admin) activa "Ejecutar ahora".
- Modal de confirmación con foco trap, Esc cancela.
- Mini-barra de ejecuciones con `aria-label` agregada "{x} de 10 ejecuciones exitosas, última: {timestamp}".
- Contraste WCAG AA en chip suspended y botones primary.

## Copy literal (español)

- Header: "Cronjobs"
- Subheader contador: "{visibles} de {total} cronjobs · {failed} con fallos"
- Placeholder search: "Buscar cronjob…"
- Filtro sistema: "Todos" / "K8s" / "n8n" / "Host"
- Filtro estado: "Estado: todos" / "Sólo fallidos" / "Sin ejecutar" / "Suspendidos"
- Toggle suspended: "Ver suspendidos"
- Tarjeta schedule prefix: "Cada", "Diario", "Semanal", "A las"
- Tarjeta CTA: "Ejecutar ahora" / "Ver histórico"
- Estado never: "Sin ejecuciones"
- Estado suspended: "Suspendido"
- Confirmación dangerous: "Vas a ejecutar manualmente {name}, marcado como acción sensible. ¿Continuar?"
- Confirmación normal: "¿Ejecutar {name} ahora?"
- Toast trigger success: "Ejecución manual encolada."
- Toast trigger error: "No se pudo encolar la ejecución."
- Empty filtros: "Ningún cronjob coincide con los filtros."
- Drawer histórico header: "Histórico de {name}"
- Drawer empty: "Aún no hay ejecuciones registradas."

## Estados

- **Loading**: skeleton de 8 tarjetas con shimmer.
- **Error**: banner rojo arriba "No se pudo cargar cronjobs · Reintentar". Mantiene última snapshot.
- **Empty (sin filtros)**: ilustración + "El módulo automation aún no se ha sincronizado."
- **Empty (con filtros)**: "Ningún cronjob coincide con los filtros." + botón Limpiar.
- **Success**: grid render. Indicador "Sincronizado hace {n}s".
- **Run-now en curso**: tarjeta muestra spinner en el botón, estado cambia a "Ejecutando…" hasta confirmación del backend.
- **Run-now success**: toast + chip se actualiza a "Success" tras polling.
- **Run-now failed**: toast rojo + chip se actualiza a "Failed", link directo al histórico.

## Cita la fuente

- Datos vienen de: módulo `automation` (`GET /api/v1/automation/cronjobs`), agregador de tres fuentes:
  - K8s CronJobs vía `cluster-watcher` `[source: ARCHITECTURE.md §3]`.
  - n8n workflows vía API REST de n8n.
  - Crontab del host vía agente local (Fase 5).
- Política de no reescritura: los 18 cronjobs de `cluster-ops` **no se reimplementan**, sólo se muestran y disparan `[source: ARCHITECTURE.md §7]`.
- Roles para "Ejecutar ahora": módulo `identity` (admin / operator), `viewer` no ve el botón `[source: ARCHITECTURE.md §3]`.

---

## Prompt para Claude Design

Copy-paste literal para https://claude.ai/design:

```
Estoy diseñando el "Cronjob Board" para un Internal Developer Platform (IDP).
Unifica 28 cronjobs reales: 18 de Kubernetes (cluster-ops), ~7 workflows de
n8n y 3 del crontab del host.

Tema: dark Material 3, OLED-optimizado, primary #00E676 (neon green),
background #0F1419, surface tarjeta #1A1E23 (hover #212529),
RoundedCornerShape(8.dp). Stack: Compose Multiplatform Web (target wasmJs).

Layout: grid responsive auto-fit con tarjetas mínimo 280dp.

Cada tarjeta:
- Header: icono del sistema (hexágono azul #326CE5 para K8s, rosa #EA4B71
  para n8n, gris para Host), nombre del cronjob, namespace o ruta.
- Body: schedule cron humanizado ("cada 5 min", "diario 08:00", "cada 4 h"),
  chip de estado (Success verde, Failed rojo, Never gris, Suspended ámbar),
  "Última: hace 3 min", "Próxima: en 2 min", y una mini-barra horizontal con
  las últimas 10 ejecuciones representadas como cuadritos 8x8 verdes/rojos
  estilo GitHub commits.
- Footer: botón outline "Ver histórico" y botón fill verde "Ejecutar ahora"
  (este último sólo visible para rol admin).

Ejemplos reales de cronjobs a mostrar en mockup:
- K8s cluster-ops: cert-checks, cluster-self-healing, emqx-checks, host-checks,
  longhorn-checks, pg-metadata-checks, redis-checks, timescale-checks,
  wireguard-checks, infra-version-watch, latest-images-rotator, log-hygiene,
  tier0-image-latest-watch, tier0-traffic-sentinel, kuma-image-updater,
  event-exporter-image-updater, heartbeat-08utc, cluster-healthcheck-discord
  (SUSPENDED, mostrar con borde ámbar).
- Host: k8s-doctor diagnose (cada 4h), k8s-doctor remediate (cada 12h),
  ssl-cert-check (diario 08:00).
- n8n: 7 cards placeholder.

Top-bar: search "Buscar cronjob…", filtros chip "Todos / K8s / n8n / Host",
dropdown namespace, dropdown estado, toggle "Ver suspendidos".

Drawer derecho al click "Ver histórico": tabla de las últimas 50 ejecuciones
(timestamp, duración, exit code, link a logs, "Ver YAML" sólo k8s) + mini
gráfico de duración temporal.

Modal de confirmación al "Ejecutar ahora":
- Normal: "¿Ejecutar {name} ahora?"
- Si el cronjob es dangerous (p. ej. cluster-self-healing): "Vas a ejecutar
  manualmente {name}, marcado como acción sensible. ¿Continuar?"

Breakpoints:
- Compact (<600dp): grid 1 columna, filtros en bottom-sheet, drawer
  histórico fullscreen.
- Medium (600-840dp): grid 2 columnas, filtros como chips horizontales
  scrollables, drawer 420dp.
- Expanded (>840dp): grid 3-4 columnas, filtros en barra superior
  persistente, drawer 480dp.

Estados: Loading skeleton 8 tarjetas, Error banner reintentar, Empty
filtrado con botón Limpiar, Success con indicador "Sincronizado hace {n}s",
Run-now en curso con spinner en el botón.

Accesibilidad: role=grid con aria-rowcount/colcount, aria-label por tarjeta
con todo el contexto, navegación por flechas, foco trap en modal de
confirmación. Contraste WCAG AA.

Copy en español neutro. Genera mockups wireframe + high-fidelity para los
3 breakpoints, mostrando tarjetas en distintos estados y el drawer histórico
abierto en uno.
```
