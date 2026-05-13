---
description: Reporta progreso de la wave activa del Agent Team — tasks pendientes/in-progress/completed por teammate, blockers, ETA estimado
allowed-tools: Read, Bash, Grep
---

Genera un snapshot rápido del estado actual de la wave en ejecución.

Complementa al comando `/status-report` que da una vista de proyecto completo. `/wave-status` es más estrecho: sólo la wave activa.

## Pasos

### 1. Detectar wave activa

```bash
# Listar teams activos del usuario
ls ~/.claude/teams/ 2>/dev/null | head -5

# Si hay > 1 team activo (no debería en v2026-05, pero just in case), elegir el más reciente
team_dir=$(ls -td ~/.claude/teams/*/ 2>/dev/null | head -1)
```

Si no hay team activo: reportar "No hay wave activa actualmente" y salir.

### 2. Leer config del team

```bash
cat "$team_dir/config.json" | jq .
```

Extraer:
- `name` del team
- `members[]` con `name`, `agent_id`, `agent_type`
- Tiempo desde creación

### 3. Listar todas las tasks de la wave

```bash
# Tasks viven en ~/.claude/tasks/<team-name>/*.json
task_dir="$HOME/.claude/tasks/$(basename $team_dir)/"
ls "$task_dir"/*.json 2>/dev/null | wc -l
```

Para cada task:
```bash
for f in "$task_dir"/*.json; do
    jq -r '"\(.id) | \(.status) | \(.owner // "—") | \(.subject)"' "$f"
done | column -t -s '|'
```

Agrupar por status:
- `pending` — sin owner, esperando
- `in_progress` — owner asignado, working
- `completed` — done

### 4. Detectar blockers

Para cada task `pending`:
- Si tiene `blockedBy[]` no resuelto → listar qué tasks la bloquean
- Si todas sus dependencias están `completed` → marcarla como **ready to claim**

Para cada task `in_progress`:
- Tiempo desde `updated_at` → si > 30 min sin update → 🟡 STALLED
- Tiempo desde `claimed_at` → si > 2h → 🔴 LONG-RUNNING

### 5. Coste estimado

```bash
# Si claude.ai/settings/usage tuviera CLI, mostrar consumo del día.
# Por ahora, estimar basándonos en duración del team y modelos:
team_age_hours=$(...)
# Asumir ~$3-5/hr por Opus teammate, ~$1-2/hr por Sonnet
```

NOTE: este cálculo es estimación, no autoritativo. Confirmar en https://claude.ai/settings/usage manualmente.

### 6. Output

```markdown
# Wave Status — <team-name>

**Created**: 2026-05-13 12:30 UTC (4h 12m ago)
**Members**: 6 teammates (1 opus, 5 sonnet)
**Estimated cost**: ~$18-25 USD (verifica en claude.ai/settings/usage)

## Tasks

| ID  | Status       | Owner             | Subject                                          |
|-----|--------------|-------------------|--------------------------------------------------|
| A1  | ✅ completed | architect         | Define inventory contracts + ADR-0005            |
| A2  | 🟢 in_progress | backend-dev-1    | Implement inventory module (started 1h ago)      |
| A3  | 🟢 in_progress | backend-dev-2    | cluster-watcher fabric8 informers                |
| A4  | 🟢 in_progress | frontend-dev     | PodsList Compose MP Web screen                   |
| A5  | ⏸  pending   | qa-engineer       | Integration tests (blocked by A2, A3)            |
| A6  | 🟢 in_progress | devops-engineer  | Helm chart + Dockerfile + CI extension           |
| A7  | ⏸  pending   | security-reviewer | Review (blocked by A2, A3, A4)                   |
| A8  | ✅ completed | tech-writer       | docs/modules/inventory.md                        |

## Blockers
- A5 espera A2, A3 → ETA ~1h (backend-devs estiman near-done)
- A7 espera A2, A3, A4 → ETA ~1h tras backend + frontend

## Stalled / Long-running
- (vacío) — todos los teammates actualizaron en los últimos 15 min

## Próximos pasos sugeridos
1. backend-dev-1 finalice A2 (estimado < 1h)
2. backend-dev-2 finalice A3 (estimado < 1h)
3. A5 y A7 se desbloquean automáticamente
4. Cuando A5..A7 completen → team-lead declara wave done + PR draft + cleanup

---
🤖 Snapshot generado por /wave-status. Para ver detalle de una task: `TaskGet <id>`.
```

## Variantes

`/wave-status --json` → output JSON estructurado para parsing por otra routine.

`/wave-status --since 1h` → sólo cambios en la última hora (deltas).

## Reglas

- Sólo lectura. NUNCA modificar tasks ni el team.
- Si el team-dir no existe, asumir que no hay wave activa (no fail).
- Si las APIs de Agent Teams cambian de path, fallback a leer `~/.claude/tasks/` directamente.
- NO leer mailbox (`~/.claude/teams/<team>/inboxes/`) — privacy entre teammates.
