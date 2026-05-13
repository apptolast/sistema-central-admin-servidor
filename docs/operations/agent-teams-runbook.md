---
title: "Agent Teams operations — arrancar, monitorear, recovery"
type: runbook
owner: pablo
source-of-truth: "https://code.claude.com/docs/en/agent-teams + .claude/agents/team-lead.md"
last-verified: 2026-05-13
tags: [agent-teams, claude-code, operations, marathon]
status: stable
phase: meta
depends-on:
  - repo:apptolast/sistema-central-admin-servidor
related-runbooks: []
---

# Agent Teams runbook — procedimiento operativo

Cómo arrancar, monitorear y recuperar un Agent Team de Claude Code en este repo, sin alucinaciones.

## Pre-requisitos verificados

- Claude Code CLI ≥ v2.1.32 (en el servidor: v2.1.140 confirmado por `claude --version`)
- `.claude/settings.json` con `"CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"` en `env` (confirmado, commit `d36c5bf`)
- Branch limpia o feature branch propia (`feat/marathon-2026-05-13` actualmente)
- Repo: `/home/admin/sistema-central-admin-servidor` (NO el cwd `/home/admin/sistema-central-infra-servidor` que está vacío y debería borrarse — sugerencia: `rmdir /home/admin/sistema-central-infra-servidor`)

## Smoke test (antes de Wave A grande)

El smoke test valida que la API de Agent Teams responde y se limpia bien antes de invertir tokens en un team de 6+ teammates.

### Procedimiento

```bash
# Terminal en el servidor o portátil con acceso al repo
cd /home/admin/sistema-central-admin-servidor
git status  # Confirmar que estamos en feat/marathon-2026-05-13 o equivalente

# Arrancar Claude Code interactivo
claude
```

Dentro de la sesión interactiva de Claude Code:

```
Spawn an agent team with 2 teammates: architect-smoke and tech-writer-smoke.
Use Sonnet for both (smoke test, cost-aware).

Architect-smoke task:
  - Read CLAUDE.md and ARCHITECTURE.md.
  - Write a 1-paragraph summary to docs/_smoke.md with frontmatter status: draft.
  - Cite at least one source: ARCHITECTURE.md §X.

Tech-writer-smoke task (blockedBy architect-smoke):
  - Read docs/_smoke.md created by the architect.
  - Add a "## Citation review" section at the end with notes on the citation.

Both teammates: when done, notify the lead via SendMessage and shut down.

After both complete, clean up the team.
```

### Done when

- `ls -la docs/_smoke.md` existe con 2 commits (1 por teammate)
- `git log --oneline -5` muestra 2 commits con autores teammate distintos
- `ls ~/.claude/teams/` está vacío o sin team `smoke-*` (cleanup OK)
- Sin orphan tmux sessions: `tmux ls 2>/dev/null` no muestra session `smoke-*`

### Si falla

| Síntoma | Causa probable | Recovery |
|---|---|---|
| "Agent Teams disabled" | env var no leída | Verificar `grep CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS .claude/settings.json`. Si está OK pero falla: `export CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` en el shell antes de `claude` |
| Teammate no aparece | in-process mode + no Shift+Down | Presiona `Shift+Down` para recorrer teammates activos |
| Teammate stuck en error | Permisos insuficientes en su prompt | SendMessage con instrucciones más concretas, o respawn con prompt ampliado |
| TeamCreate falla con HTTP 500 | API en research preview, ocasional | Reintentar tras 30s; si persiste, ver §"Fallback" |
| Sesión tmux huérfana tras cleanup | bug Agent Teams v2026-05 | `tmux ls; tmux kill-session -t <smoke-*>` manualmente |

## Wave A — Fase 1 (inventory + cluster-watcher + UI esqueleto)

Cuando el smoke test pase, arrancar Wave A real con 6-8 teammates.

### Procedimiento

```bash
cd /home/admin/sistema-central-admin-servidor
git status
claude
```

Dentro de Claude Code:

1. Pega el **prompt inicial al team-lead** desde [`docs/operations/wave-A-prompts.md`](./wave-A-prompts.md) §1
2. El lead lee el contexto, hace `TeamCreate fase1-inventory`, y empieza a crear tasks
3. Cuando el lead pida confirmación o detalle, copia/pega los prompts §2-§9 correspondientes a cada teammate
4. Activa Delegate Mode con `Shift+Tab` (lead bloquea Bash/Write/Edit, solo coordina)
5. Monitoreo: `Ctrl+T` muestra TaskList; `Shift+Down` recorre teammates; en tmux mode cada teammate tiene panel propio

### Tiempo estimado

| Fase | Duración estimada | Tokens estimados (rough) |
|---|---|---|
| Setup + smoke | 30 min | ~50k tokens |
| Wave A (6 teammates, Opus + Sonnet mix) | 4-8 horas | ~500k tokens |
| Wave B (4 teammates) | 2-4 horas | ~250k tokens |
| PR + cleanup | 30 min | ~30k tokens |

**Total maratón día**: 8-12h continuas; tokens ~800k-1M. Verifica usage en https://claude.ai/settings/usage cada 1-2h.

### Cost gate (umbral de stop)

Si tras 4h del marathon el usage del día supera 75% del límite:
1. Lead detiene spawn de teammates nuevos
2. Espera a que los activos completen tareas en curso
3. Cleanup team
4. PR draft con lo hecho
5. Continuar Wave B al día siguiente

## Wave B — Fase 2 arranque

Tras Wave A merged a `main`, arrancar Wave B con un team nuevo (no continuar el de Wave A — limitación documentada: 1 team por sesión, lead fijo):

```
Crea team fase2-observability con 4 teammates según docs/operations/wave-B-prompts.md
```

(Detalle en `wave-B-prompts.md`).

## Recovery — qué hacer cuando algo se rompe

### Lead pierde awareness del team tras compaction

Síntoma: tras compaction, lead intenta SendMessage a teammates inexistentes o niega que hay team activo.

Recovery:
```bash
# En otro terminal
cat ~/.claude/teams/fase1-inventory/config.json | jq '.members'
```

Y en la sesión Claude Code:
```
Mira ~/.claude/teams/fase1-inventory/config.json. Tienes un team activo con N teammates.
Recupera awareness y continúa la wave.
```

### Teammate stuck > 30 min sin TaskUpdate

```
Lead: envía SendMessage a <teammate-name>: "¿En qué punto estás? ¿hay blockers? Reporta status en 60s o haré shutdown."

Si en 60s no responde:
  Lead: shutdown del teammate
  Lead: spawn de reemplazo con prompt ampliado que incluya:
    "El teammate anterior se quedó stuck en <X>. Recoge desde <Y> y termina."
```

### Sesión Claude Code crashea o se pierde conexión SSH

Limitación documentada (https://code.claude.com/docs/en/agent-teams §Limitations):
- En modo `in-process`: los teammates mueren con el lead
- En modo `tmux`: los teammates persisten — al reabrir `claude` y hacer `/resume`, el lead puede recuperar

Recovery con tmux:
```bash
export CLAUDE_CODE_SPAWN_BACKEND=tmux
claude
# Dentro: /resume → seleccionar la sesión del team

# Si el resume no encuentra teammates pero las sesiones tmux están vivas:
tmux ls | grep fase1
# Adjuntar manualmente y dar shutdown ordenado:
tmux attach -t fase1-inventory-backend-dev-1
# (Escribe en el panel: "team-lead murió. Termina tu task actual, commit lo que tengas, y exit.")
```

### Cleanup parcial — team-dir queda colgado

```bash
ls ~/.claude/teams/
# Si hay teams sin terminar:
rm -rf ~/.claude/teams/<team-name>/
ls ~/.claude/tasks/<team-name>/  # Backup antes de borrar si las tasks son útiles
rm -rf ~/.claude/tasks/<team-name>/
```

### Sesiones tmux huérfanas

```bash
tmux ls
tmux kill-session -t <session-name>
```

## Fallback — Agent Teams no disponible

Si por alguna razón el flag `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` no surte efecto (por ejemplo en una versión de Claude Code que retira la feature):

### Plan B — Sub-agents + git worktrees

```bash
cd /home/admin/sistema-central-admin-servidor

# Crear worktree por área de ownership
git worktree add ../wave-a-backend feat/wave-a-backend
git worktree add ../wave-a-frontend feat/wave-a-frontend
git worktree add ../wave-a-devops feat/wave-a-devops

# En 3 terminales separadas, claude en cada worktree
# Sesión 1
cd ../wave-a-backend && claude
# Pega prompt del backend-dev #1 desde wave-A-prompts.md §3 al inicio del chat

# Sesión 2
cd ../wave-a-frontend && claude
# Pega prompt del frontend-dev §5

# Sesión 3
cd ../wave-a-devops && claude
# Pega prompt del devops-engineer §7

# Coordinación: PRs cruzados a main; el lead role lo cumple Pablo manualmente
```

Limitaciones del fallback:
- No hay TaskList compartida entre sesiones → coordinación manual
- No hay SendMessage entre teammates → cada uno se entera de los demás al ver merges en main
- Tokens similares (3 sesiones full vs 6 teammates en team)
- Pierdes auto-coordinación, ganas robustez (sesiones independientes)

### Plan C — Sub-agents Task tool clásico

Desde un solo `claude`:

```
Spawn 3 sub-agents in parallel using the Agent tool:
- Agent 1 (general-purpose): implement platform/inventory/ following docs/operations/wave-A-prompts.md §3
- Agent 2 (general-purpose): implement services/cluster-watcher/ following §4
- Agent 3 (general-purpose): implement frontend/composeApp/...inventory/ following §5

When all 3 complete, summarize and create a single PR with all changes.
```

Limitaciones del Plan C:
- Sub-agents reportan al padre, no se hablan entre sí
- Single context window del padre crece rápido con 3+ sub-agents → riesgo de compaction
- Lo más sensato si Agent Teams no funciona pero queremos paralelismo simple

## Indicadores de éxito de cada wave

### Wave A done

- Branch `feat/wave-a-...` merged a `main`
- `./gradlew :inventory:build :inventory:test` verde
- `./gradlew :platform-app:test --tests "*Modulith*"` verde (reactivado desde commit `2f30755`)
- `helm lint k8s/helm/platform/` verde
- `docs/modules/inventory.md` con frontmatter completo
- `docs/progress/2026-W19.md` (o equivalente) escrito por el lead
- `gh pr view <N> --json mergedAt` no null
- Issue label `phase-1` cerrado (si se abrió previamente)

### Wave B done

- 2 ADRs nuevos en `docs/adrs/` (0005-nats-vs-inmemory-bus, 0006-otel-collector-shape)
- `helm template k8s/helm/otel-collector/` válido (kubeconform pass)
- `platform/observability/` con `@ApplicationModule` package-info.java
- `./gradlew :observability:test --tests "*Architecture*"` verde

## Limitaciones documentadas (research preview v2026-05)

Fuente: https://code.claude.com/docs/en/agent-teams §Limitations

- **No session resumption con teammates in-process**: `/resume` y `/rewind` no restauran teammates in-process. Workaround: `CLAUDE_CODE_SPAWN_BACKEND=tmux`.
- **Un team por sesión**: para más paralelismo, git worktrees con sesiones independientes.
- **No file locking en ediciones**: task claiming usa lock; ediciones no. Defensa: ownership estricto en `.claude/rules/ownership.md`.
- **Compaction puede perder team awareness**: workaround manual leyendo `~/.claude/teams/<name>/config.json`.
- **Delegate Mode demasiado restrictivo**: en algunas builds, teammates heredan restricciones del lead. Workaround: default mode con allowlists generosas (ya configuradas en `.claude/settings.json`).
- **Split panes requieren tmux o iTerm2**: in-process funciona en cualquier terminal pero menos visual.

## Smoke test resumido en 5 líneas

```
Pablo en terminal:                    cd /home/admin/sistema-central-admin-servidor && claude
Pablo en chat de Claude Code:         Spawn smoke team with 2 teammates (Sonnet), Architect writes docs/_smoke.md, tech-writer adds review. Cleanup.
Pablo en otro terminal:               ls docs/_smoke.md && cat docs/_smoke.md && ls ~/.claude/teams/
Si pasa:                              Continúa con Wave A (docs/operations/wave-A-prompts.md)
Si falla:                             Usa Plan B (worktrees) o Plan C (sub-agents)
```

## Cita la fuente

- Agent Teams docs: `https://code.claude.com/docs/en/agent-teams` (verificado 2026-05-13)
- Settings: [`.claude/settings.json`](../../.claude/settings.json) commit `d36c5bf`
- Lead agent: [`.claude/agents/team-lead.md`](../../.claude/agents/team-lead.md)
- Ownership: [`.claude/rules/ownership.md`](../../.claude/rules/ownership.md)
- Estado real cluster: dossier en conversación 2026-05-12 (no en repo todavía; pendiente migrar a `docs/infrastructure/cluster-baseline-2026-05-12.md`)
