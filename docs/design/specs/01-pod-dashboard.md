---
title: "Pod Dashboard — lista de pods del cluster"
type: design-spec
owner: pablo
phase: 1
last-verified: 2026-05-13
status: draft
related-modules:
  - inventory
  - observability
source-of-truth: "kubectl get pods --all-namespaces -o json"
tags:
  - frontend
  - inventory
  - pods
depends-on:
  - module:inventory
  - service:cluster-watcher
related-runbooks:
  - RB-20-OOMKILLED-REPEAT
  - RB-12-pod-crashloopbackoff
see-also:
  - ../../adrs/0002-compose-multiplatform-web-vs-react.md
  - ../../../ARCHITECTURE.md
---

# Pod Dashboard

## User story

Como admin, quiero ver el estado de los 126 pods del cluster `[source: dossier-2026-05-12]` en una pantalla con filtros para diagnosticar rápido cuándo algo falla, sin tener que abrir Rancher ni hacer `kubectl get pods` en SSH.

## Componentes

- **Top-bar (`SearchableTopBar` heredado de GreenhouseAdmin)**: campo búsqueda full-width, contador "X de 126 pods", botón refresh manual, dropdown namespace, toggles de estado.
- **Main area**: tabla virtualized con scroll vertical infinito (LazyColumn / DataTable). Header sticky.
- **Side panel (expanded breakpoint)**: panel derecho 320dp con resumen agregado: pods por estado (Running / Pending / Failed / CrashLoopBackOff / Completed), pods por nodo (1 nodo: VPS Hetzner CPX62), restarts totales últimas 24h.
- **Empty state**: ilustración minimal + texto "No hay pods que coincidan con los filtros".
- **Row click**: abre el drawer del [Pod Detail](./02-pod-detail.md).

## Columnas de la tabla

| Columna | Ancho | Tipo de celda |
|---|---|---|
| Namespace | 200dp | texto + tag color por categoría |
| Name | flex | `CopyableIdCell` (copy al click) |
| Status | 140dp | `StatusChip` con color semantic (success=Running, warning=Pending, error=Failed/CrashLoopBackOff, neutral=Completed) |
| Ready | 80dp | "1/1", "0/2" en color |
| Restarts | 80dp | badge rojo si > 0 + tooltip con "última reinicio: hace X min" |
| Age | 100dp | humanizado (e.g., "3d 4h") |
| Node | 180dp | texto truncado + tooltip con FQDN |

## State / Props

- **ViewModel**: `PodsViewModel(inventoryClient: InventoryClient, scope: CoroutineScope)`.
- **Flows**:
  - `pods: StateFlow<List<Pod>>` — proviene de `GET /api/v1/inventory/pods` (módulo `inventory`) `[source: ARCHITECTURE.md §3]`.
  - `filters: MutableStateFlow<PodFilters>` — namespace, estado, search query, "última hora".
  - `filtered: StateFlow<List<Pod>>` = combine(pods, filters).
- **Polling**: refresh cada 10 s vía SSE/WebSocket del `cluster-watcher`. Fallback REST cada 30 s si la conexión cae.

## Filtros

- **Search top-bar**: substring match contra `name` o `namespace`, case-insensitive.
- **Dropdown namespace**: lista de los 36 namespaces reales `[source: dossier-2026-05-12]`. Algunos ejemplos: `apptolast-greenhouse-admin-dev`, `apptolast-inemsellar`, `apptolast-invernadero-api`, `apptolast-invernadero-mqtt`, `apptolast-tier0-public`, `cluster-ops`, `longhorn-system`, `kube-system`, `cattle-system`, `fleet-system`, `keycloak`, `n8n`, `passbolt`, `traefik`. Opción "Todos" por defecto.
- **Toggle "Sólo Failed / CrashLoopBackOff"**: filtra `status in {Failed, CrashLoopBackOff, ImagePullBackOff, OOMKilled, Error}`.
- **Toggle "Sólo última hora"**: filtra `age < 1h`.

## Layout responsive

| Breakpoint | Layout |
|---|---|
| Compact (<600dp) | NavigationBar abajo. Tabla colapsa a tarjetas verticales (1 columna). Filtros en bottom-sheet. Side panel oculto. |
| Medium (600-840dp) | NavigationRail izquierda. Tabla con 4 columnas visibles (Namespace, Name, Status, Restarts), resto en expand. Side panel oculto. |
| Expanded (>840dp) | Sidebar izquierda 240dp + tabla full + side panel derecho 320dp. Las 7 columnas visibles. |

## Theming

- Background: `#0F1419`.
- Surface tabla: `#1A1E23`.
- Primary (selección row, focus, botón refresh): `#00E676`.
- Texto primario: `#FFFFFF` (alpha 0.87).
- Texto secundario: `#FFFFFF` (alpha 0.60).
- Semantic:
  - success Running: `#00E676`
  - warning Pending: `#FFB300`
  - error Failed/CrashLoopBackOff/OOMKilled: `#FF5252`
  - neutral Completed: `#9E9E9E`
- Tipografía Material 3:
  - title-medium para header tabla.
  - body-medium para celdas.
  - label-small para chips y badges.
- Bordes: `RoundedCornerShape(8.dp)` en tarjetas y chips.

## Accesibilidad

- ARIA roles: `role=table`, `role=row`, `role=cell`, `role=columnheader`. Header con `aria-sort`.
- Navegación por teclado:
  - Tab entra a la tabla.
  - Flechas arriba/abajo navegan rows.
  - Enter abre el drawer del pod (equivalente al click).
  - `/` enfoca el campo búsqueda.
  - Esc limpia filtros o cierra el drawer.
- Contraste WCAG AA mínimo: verificado para `#00E676` sobre `#0F1419` (ratio 10.3:1).
- `Modifier.semantics { contentDescription = "Pod $name en estado $status" }` por row para screen readers.
- Anuncio de cambios en la lista (`liveRegion = LiveRegionMode.Polite`) cuando entran/salen pods.

## Copy literal (español)

- Header: "Pods del cluster"
- Subheader contador: "{filtrados} de {total} pods · {namespaces} namespaces"
- Placeholder search: "Buscar por nombre o namespace…"
- Toggle estado: "Sólo problemas"
- Toggle tiempo: "Última hora"
- Empty state: "No hay pods que coincidan con los filtros."
- Empty cluster: "El inventario aún no se ha sincronizado. Espera unos segundos…"
- Tooltip restarts: "{n} reinicios. Último: hace {time}."
- CTA refresh: "Refrescar ahora"

## Estados

- **Loading**: skeleton de 10 filas con shimmer sobre `#1A1E23`. Top-bar y filtros deshabilitados.
- **Error**: banner rojo arriba con "No se pudo cargar el inventario · Reintentar". Mantiene la última snapshot conocida en gris.
- **Empty (sin filtros activos)**: ilustración cluster vacío + texto "El inventario aún no se ha sincronizado."
- **Empty (con filtros)**: "No hay pods que coincidan con los filtros." + botón "Limpiar filtros".
- **Success**: tabla render. Indicador "Sincronizado hace {n}s" abajo a la derecha.

## Cita la fuente

- Datos vienen de: módulo `inventory` (`GET /api/v1/inventory/pods`) `[source: ARCHITECTURE.md §3]`, alimentado por `cluster-watcher` vía fabric8 informers `[source: ARCHITECTURE.md §2.2]`.
- Decisión arquitectónica frontend: `ADR-0002` (Compose Multiplatform Web vs React/Vue).
- Volumetría: 126 pods en 36 namespaces `[source: dossier-2026-05-12]`.

---

## Prompt para Claude Design

Copy-paste literal para https://claude.ai/design:

```
Estoy diseñando la pantalla "Pod Dashboard" para un Internal Developer Platform (IDP)
single-tenant sobre Kubernetes single-node.

Tema: dark Material 3, OLED-optimizado, primary #00E676 (neon green),
background #0F1419, surface #1A1E23, RoundedCornerShape(8.dp).
Stack: Compose Multiplatform Web (target wasmJs).

Layout principal: tabla virtualized para 126 pods reales del cluster, distribuidos
en 36 namespaces. Columnas: Namespace (con tag de color por categoría), Name
(copyable), Status (chip semantic: Running verde neon, Pending ámbar,
Failed/CrashLoopBackOff rojo, Completed gris), Ready (1/1, 0/2), Restarts (badge
rojo si > 0), Age humanizado, Node. Header sticky. Row click abre drawer derecho
de detalle.

Top-bar: campo de búsqueda full-width con placeholder "Buscar por nombre o
namespace…", contador "X de 126 pods", dropdown de namespace (lista los 36
namespaces reales, ejemplos: apptolast-greenhouse-admin-dev, apptolast-inemsellar,
cluster-ops, longhorn-system, kube-system, n8n, keycloak, traefik), toggle "Sólo
problemas" (Failed/CrashLoopBackOff), toggle "Última hora", botón refresh.

Side panel derecho (sólo expanded breakpoint, 320dp): resumen agregado con
pods por estado, pods por nodo, restarts totales 24h.

Tres breakpoints:
- Compact (<600dp): NavigationBar inferior, tabla colapsa a tarjetas verticales,
  filtros en bottom-sheet, side panel oculto.
- Medium (600-840dp): NavigationRail izquierda, tabla con 4 columnas visibles,
  side panel oculto.
- Expanded (>840dp): Sidebar 240dp + tabla full 7 columnas + side panel 320dp.

Estados: Loading (skeleton shimmer 10 filas), Error (banner rojo "No se pudo
cargar el inventario · Reintentar"), Empty con filtros ("No hay pods que
coincidan con los filtros." + botón Limpiar), Success con indicador
"Sincronizado hace {n}s" abajo a la derecha.

Accesibilidad: ARIA role=table/row/cell, navegación por teclado (flechas, Enter,
"/" para foco búsqueda, Esc para limpiar), contraste WCAG AA verificado.

Tipografía Material 3: title-medium en headers, body-medium en celdas,
label-small en chips. Copy en español neutro.

Genera mockups en wireframe + high-fidelity para los 3 breakpoints
(compact, medium, expanded).
```
