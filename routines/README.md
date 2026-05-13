# Claude Code Routines

> Configuraciones de [Claude Code Routines](https://code.claude.com/docs/en/routines) que orquestan trabajo autónomo sobre este repo desde la infraestructura cloud administrada por Anthropic.

---

## Qué son

Cada `.yaml` de este directorio describe **una rutina**: una sesión de Claude Code que se ejecuta automáticamente según uno o más triggers (schedule, GitHub event, o API), trabaja sobre el repo y los conectores configurados, y produce algo (PRs, comentarios, issues, reportes).

Las rutinas **se ejecutan en la nube de Anthropic** — no en tu portátil ni en el cluster. Tu portátil puede estar apagado y siguen funcionando.

## Fuente de verdad

Los YAML aquí son **el source-of-truth versionado** de cada rutina. La configuración real vive en [claude.ai/code/routines](https://claude.ai/code/routines) en la cuenta del owner. **Cuando edites un YAML, actualiza también la rutina en claude.ai** — o configura una GitHub Action que los sincronice (TODO Fase 6).

## Cómo crear/actualizar una rutina

1. Edita el YAML aquí.
2. Abre https://claude.ai/code/routines (con tu cuenta del owner — Pro/Max plan requerido).
3. Crea una rutina nueva (o edita la existente) copiando:
   - `name`, `description` → al formulario
   - `prompt` → al campo "Instructions"
   - `trigger.schedule.cron` (si aplica) → cron schedule
   - `trigger.github.event` (si aplica) → GitHub event trigger
   - `trigger.api` (si aplica) → genera URL + token
   - `repositories` → checklist de repos
   - `connectors` → checklist de connectors
   - `environment` → entorno (default o custom)
   - `allow_unrestricted_branch_pushes` → toggle
4. Commit el cambio del YAML para registrar la nueva versión.

## Rutinas activas

| Rutina | Trigger | Qué hace | Estado |
|--------|---------|----------|--------|
| [`phase-progress-report`](./phase-progress-report.yaml) | Lunes 09:00 UTC | Genera reporte semanal de progreso y abre PR a `docs/progress/` | Por habilitar |
| [`pr-reviewer`](./pr-reviewer.yaml) | GitHub `pull_request.opened` | Revisa cada PR según checklist de calidad y deja comentarios | Por habilitar |
| [`nightly-arch-review`](./nightly-arch-review.yaml) | Diaria 02:00 UTC | Verifica invariantes de arquitectura y abre issue si hay regresiones | Por habilitar |

## Rutinas planificadas (Phase 3+)

- `docs-drift-detector` — Compara estado real del cluster vs `docs/` y propone PRs de actualización (necesita Fase 1 + Fase 3).
- `inventory-sync-watchdog` — Verifica que el `cluster-watcher` está sincronizando OK (necesita Fase 1).
- `dependency-update-radar` — Domingo 06:00 UTC, escanea releases de Spring/Kotlin/Keycloak.
- `backup-verification` — Diaria 03:00 UTC, verifica que el backup nocturno Longhorn → Hetzner Storage Box terminó (Fase 6).
- `runbook-execution-trigger` — Endpoint API que recibe `{runbook, params}` y los ejecuta con confirmación humana.

## Convenciones

- **Branches creadas por rutinas**: siempre prefijo `claude/` (cumple la regla de branch protection).
- **Mensajes de PR**: deben incluir `🤖 Automated PR from routine '<routine-name>'` en el cuerpo.
- **No fixes silenciosos**: las rutinas REPORTAN problemas, no los arreglan. La excepción es `phase-progress-report` (sólo escribe en `docs/progress/`).
- **Sin acceso a secrets de runtime**: las rutinas usan los conectores configurados en claude.ai (GitHub, etc.). No accesan a Passbolt, Keycloak, ni K8s API en runtime.
