---
title: "Pod Detail — drawer derecho con detalle de un pod"
type: design-spec
owner: pablo
phase: 1
last-verified: 2026-05-13
status: draft
related-modules:
  - inventory
  - observability
  - identity
source-of-truth: "kubectl -n <ns> describe pod <name>"
tags:
  - frontend
  - inventory
  - pods
  - detail
depends-on:
  - module:inventory
  - module:identity
  - service:cluster-watcher
related-runbooks:
  - RB-20-OOMKILLED-REPEAT
  - RB-12-pod-crashloopbackoff
see-also:
  - ./01-pod-dashboard.md
  - ./03-runbook-viewer.md
  - ../../../ARCHITECTURE.md
---

# Pod Detail

## User story

Como admin, al hacer click en una fila del [Pod Dashboard](./01-pod-dashboard.md), quiero abrir un drawer con el detalle completo del pod (containers, env vars saneados, recursos, volúmenes, eventos K8s y acciones operativas) sin perder el contexto de la tabla original.

## Componentes

- **Drawer header**:
  - `CopyableIdCell` con `namespace/name` (un solo string, copyable).
  - `StatusChip` con estado.
  - Botón cerrar (X) arriba a la derecha.
  - Subheader: imagen principal, nodo, edad.
- **Tabs / secciones colapsables** (orden vertical):
  1. **Containers**: cards apilados por container con `image`, `status`, `restarts`, `lastTermination` (timestamp + reason).
  2. **Variables de entorno**: tabla key/value. Valores marcados como secret aparecen como `REDACTED` con icono candado y tooltip "Origen: Secret/<name>".
  3. **Recursos**: tabla con `requests` y `limits` de CPU y memoria por container.
  4. **Volúmenes montados**: lista con `mountPath`, `name`, tipo (PVC / ConfigMap / Secret / emptyDir). Si es PVC, link al detalle del PVC (Fase 2).
  5. **Eventos K8s**: últimos 20 eventos del pod, ordenados desc por `lastTimestamp`. Cada evento: `type` (Normal/Warning), `reason`, `message`, `count`, `age`.
  6. **Log tail** (placeholder Fase 2): card con texto "Disponible en Fase 2 — se conectará a Loki" y link al ADR pendiente.
- **Action bar (footer del drawer, sticky)**:
  - "Copy YAML" — copia al portapapeles el manifest completo.
  - "Open in Rancher" — link externo a `https://rancher.apptolast.com/.../pod/<ns>/<name>`.
  - "Ejecutar runbook" — dropdown con runbooks sugeridos según `status` (e.g., `status=OOMKilled` → sugiere `RB-20-OOMKILLED-REPEAT`). Click abre el [Runbook Viewer](./03-runbook-viewer.md).
  - "Reiniciar pod" — botón rojo, sólo visible si el usuario tiene rol `admin` (módulo `identity` `[source: ARCHITECTURE.md §3]`). Pide confirmación modal.

## State / Props

- **ViewModel**: `PodDetailViewModel(podId: PodId, inventoryClient, identityClient)`.
- **Flows**:
  - `pod: StateFlow<Pod?>` — fetch detallado `GET /api/v1/inventory/pods/{ns}/{name}`.
  - `events: StateFlow<List<K8sEvent>>` — `GET /api/v1/inventory/pods/{ns}/{name}/events?limit=20`.
  - `userRole: StateFlow<Role>` — del módulo `identity`, determina visibilidad de "Reiniciar pod".
- **Trigger**: navegación con `pod_id` en la URL (deep-linkable: `/pods/<ns>/<name>`).

## Layout responsive

| Breakpoint | Layout |
|---|---|
| Compact (<600dp) | Drawer ocupa pantalla completa. Botón "Volver" sustituye al X. Footer action bar visible siempre. |
| Medium (600-840dp) | Drawer 420dp from right, overlay semitransparente sobre la tabla. |
| Expanded (>840dp) | Drawer 480dp from right, sin overlay (tabla sigue visible y operable). |

## Theming

- Background drawer: `#1A1E23` (surface) sobre overlay `#0F1419` con alpha 0.6 (compact/medium).
- Primary acciones positivas: `#00E676`.
- Acción destructiva "Reiniciar pod": `#FF5252` con borde.
- Cards de container: `#212529` (surface elevated) con `RoundedCornerShape(8.dp)`.
- Badge `REDACTED`: fondo `#37474F`, icono candado en `#FFB300`.
- Tipografía Material 3:
  - title-large para header pod name.
  - title-small para títulos de sección.
  - body-medium para contenido.
  - label-small para chips de eventos.

## Accesibilidad

- ARIA `role=dialog` con `aria-modal=true` (compact/medium); `role=complementary` (expanded, no modal).
- `aria-labelledby` apunta al header con namespace/name.
- Foco trap dentro del drawer cuando es modal (compact/medium).
- Esc cierra el drawer.
- Tab navega por secciones; Enter en CTA acciona.
- Botón "Reiniciar pod" con `aria-describedby` que avisa "Acción destructiva, requiere confirmación".
- Screen reader anuncia "Drawer de detalle abierto para pod {name}" al abrir.
- Contraste WCAG AA garantizado para botón rojo sobre surface.

## Copy literal (español)

- Header: "{namespace} / {name}"
- Sección containers: "Containers"
- Sección env: "Variables de entorno"
- Tooltip secret: "Valor protegido. Origen: Secret/{name}"
- Sección recursos: "Recursos (requests / limits)"
- Sección volúmenes: "Volúmenes montados"
- Sección eventos: "Eventos recientes"
- Sección logs placeholder: "Log tail disponible en Fase 2. Por ahora consulta Dozzle o `kubectl logs`."
- CTAs: "Copiar YAML", "Abrir en Rancher", "Ejecutar runbook", "Reiniciar pod"
- Confirmación restart: "¿Reiniciar el pod {name}? Esta acción es inmediata y no puede deshacerse."
- Cancel modal: "Cancelar"
- Confirm modal: "Sí, reiniciar"
- Empty events: "Sin eventos recientes."
- Toast success copy YAML: "YAML copiado al portapapeles."

## Estados

- **Loading**: skeleton de header + 3 cards de sección con shimmer.
- **Error**: card central con texto "No se pudo cargar el pod. Puede haber sido eliminado del cluster." + botón "Cerrar" y "Reintentar".
- **Empty (pod eliminado mientras estaba abierto)**: igual al error pero con copy "El pod ya no existe en el cluster."
- **Success**: secciones renderizadas.
- **Action loading (restart)**: botón muestra spinner inline + deshabilitado.
- **Action success**: toast inferior "Reinicio solicitado. El pod aparecerá como Pending."
- **Action denied (sin rol)**: el botón no aparece. Si llega por API directa: toast "No tienes permisos para esta acción."

## Cita la fuente

- Datos vienen de:
  - Pod + containers + env + resources + volumes: módulo `inventory` (`GET /api/v1/inventory/pods/{ns}/{name}`).
  - Eventos K8s: módulo `observability` (`GET /api/v1/observability/events?pod={ns}/{name}&limit=20`) `[source: ARCHITECTURE.md §3]`.
  - Rol del usuario: módulo `identity` (claim OIDC del JWT Keycloak) `[source: ARCHITECTURE.md §3 + Fase 4]`.
- Decisión arquitectónica: redacción de secrets → `ADR-0002` y principio de **inventario de secrets, nunca contenido** `[source: ARCHITECTURE.md §3 módulo secrets]`.

---

## Prompt para Claude Design

Copy-paste literal para https://claude.ai/design:

```
Estoy diseñando el "Pod Detail Drawer" para un Internal Developer Platform (IDP)
sobre Kubernetes single-node. Se abre desde click en row del Pod Dashboard.

Tema: dark Material 3, OLED-optimizado, primary #00E676 (neon green),
background #0F1419, surface drawer #1A1E23, RoundedCornerShape(8.dp).
Stack: Compose Multiplatform Web (target wasmJs).

Header del drawer: namespace/name como un solo string copyable (icono copy
inline), chip semantic con estado actual del pod (Running verde,
Failed/CrashLoopBackOff rojo, Pending ámbar), botón X cerrar. Subheader con
imagen principal, nodo y edad.

Secciones verticales colapsables:
1. Containers: cards apilados con image, status, restarts, lastTermination
   (timestamp + reason).
2. Variables de entorno: tabla key/value. Valores secretos en badge REDACTED
   gris oscuro con icono candado ámbar.
3. Recursos: tabla compacta requests vs limits (CPU, memoria) por container.
4. Volúmenes montados: mountPath, name, tipo (PVC/ConfigMap/Secret/emptyDir).
5. Eventos K8s últimos 20: type (Normal/Warning), reason, message, count, age.
6. Log tail: card placeholder "Disponible en Fase 2".

Footer sticky con botones: "Copiar YAML", "Abrir en Rancher" (link externo),
"Ejecutar runbook" (dropdown sugerido por estado, p. ej. OOMKilled →
RB-20-OOMKILLED-REPEAT), "Reiniciar pod" (botón rojo destructivo, sólo visible
para rol admin, requiere modal de confirmación).

Breakpoints:
- Compact (<600dp): fullscreen, botón "Volver" en lugar de X.
- Medium (600-840dp): drawer 420dp desde la derecha con overlay semitransparente.
- Expanded (>840dp): drawer 480dp desde la derecha sin overlay, tabla sigue
  operable detrás.

Estados: Loading (skeleton shimmer), Error con CTA Reintentar, Empty si el pod
fue eliminado ("El pod ya no existe en el cluster."), Success.

Accesibilidad: role=dialog con aria-modal en compact/medium, foco trap, Esc
cierra, anuncio screen reader al abrir, contraste WCAG AA en botón rojo.

Copy en español neutro. Confirmación destructiva: "¿Reiniciar el pod {name}?
Esta acción es inmediata y no puede deshacerse."

Genera mockups wireframe + high-fidelity para los 3 breakpoints, mostrando el
drawer abierto sobre el Pod Dashboard.
```
