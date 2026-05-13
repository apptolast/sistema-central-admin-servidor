---
description: Arranca la wave de una Fase del roadmap como Agent Team — leer ARCHITECTURE.md §4, crear tareas con dependencies, spawn teammates con prompts autosuficientes
allowed-tools: Read, Grep, Bash
argument-hint: <phase-number> [--gate]
---

Arranca la Fase `$ARGUMENTS` del roadmap del proyecto.

Si el argumento incluye `--gate`, ejecuta sólo el quality gate de la Fase (skill `phase-gate-check`) sin crear team.

## Modo normal: kickoff de una wave

### 1. Validar argumento

`$ARGUMENTS` debe ser un número entero 0..7. Si vacío o malformado, preguntar al usuario.

### 2. Leer contexto

- `CLAUDE.md` (convenciones)
- `ARCHITECTURE.md` §4 (roadmap) — sección de la Fase pedida
- `ARCHITECTURE.md` §6 (restricciones operacionales — RAM, disco, single-node)
- ADRs relevantes (al menos 0001..0004)
- `docs/marathon-plan.md` si existe — buscar la wave correspondiente a esta Fase
- Si la Fase tiene runbooks asociados en `docs/runbooks/**`, listarlos
- `routines/README.md` — si la Fase activa alguna routine, mencionarlo

### 3. Auditar estado actual

```bash
# Qué hay ya en el repo de esta fase
case "$ARGUMENTS" in
  1) ls platform/inventory 2>/dev/null && ls services/cluster-watcher 2>/dev/null ;;
  2) ls platform/observability 2>/dev/null && ls k8s/helm/otel-collector 2>/dev/null ;;
  3) ls platform/knowledge 2>/dev/null && ls services/rag-* 2>/dev/null ;;
  # ... etc
esac

# Tasks abiertas de esta fase
gh issue list --label "phase-$ARGUMENTS" --state open
gh pr list --search "Phase $ARGUMENTS" --state open
```

### 4. Decidir si arrancar wave nueva o continuar

- Si hay PR de la wave anterior unmerged → NO arrancar nueva wave, primero esperar
- Si la fase está near-complete según `phase-gate-check` → ofrecer ejecutar el gate en lugar de spawn team
- Si el repo está limpio en main y la fase no tiene trabajo previo → spawn team

### 5. Spawn del Agent Team

Invocar al team-lead con prompt:

```
Eres el team-lead. Vamos a arrancar la wave de Fase $ARGUMENTS.

Lee:
- CLAUDE.md
- ARCHITECTURE.md §4 fila Fase $ARGUMENTS
- .claude/rules/{ownership,citation-policy,modulith-rules}.md
- docs/marathon-plan.md sección "Fase $ARGUMENTS"

Pasos:
1. TeamCreate con nombre `fase$ARGUMENTS-<feature-corto>`
2. TaskCreate batch con las tareas de la wave (consulta docs/marathon-plan.md
   para la lista exacta + dependencies blockedBy)
3. Spawn teammates según el tamaño recomendado en el plan:
   - Fase 1: 6 teammates (architect, 2 backend-dev, frontend-dev, qa-engineer, devops-engineer, security-reviewer)
   - Fase 2: 4 teammates (architect, backend-dev, devops-engineer, qa-engineer)
   - Fase 3: 5 teammates (architect, 2 backend-dev (uno R2R facade), frontend-dev, qa-engineer)
   - Fase 4: 4 teammates (architect, backend-dev, security-reviewer, devops-engineer)
   - Fase 5: 3 teammates (architect, backend-dev, frontend-dev)
   - Fase 6: 5 teammates (architect, 2 devops-engineer, security-reviewer, qa-engineer)
   - Fase 7: 4 teammates (architect, backend-dev, frontend-dev, tech-writer)
4. Modelos según .claude/agents/team-lead.md §"Diseño del team": Opus en architect/security-reviewer/code-reviewer/mentor; Sonnet en el resto.
5. Activa Delegate Mode (Shift+Tab) si el team > 4 teammates.
6. Monitorea cada N minutos vía TaskList; si un teammate stuck > 30min, SendMessage.
7. Al completar todas las tareas: cleanup team + create PR draft + sintetiza en docs/progress/.
```

### 6. Notificar al usuario

Mostrar al usuario:
- Qué Fase se arrancó
- Cuántos teammates spawned y con qué modelos
- Link al TaskList (`/tasks` o equivalente)
- Recordatorio: `Shift+Down` para ver teammates, `Ctrl+T` para task list, `Shift+Tab` para Delegate Mode

## Modo `--gate`: ejecutar quality gate sin team

Si `$ARGUMENTS` incluye `--gate` (ej. `/phase-kickoff 1 --gate`):

```bash
# Invocar la skill phase-gate-check para la fase pedida
.claude/skills/phase-gate-check/check.sh --phase <N>
```

Output formato dashboard. Exit code 0 si ready para declarar done, 1 si quedan items.

## Reglas

- NUNCA spawn team si la fase ya está done (verifica con phase-gate-check primero)
- NUNCA spawn team si hay PR pending de wave anterior
- NUNCA omitir el architect en una wave nueva — siempre define contratos primero
- Si el usuario quiere skip directly a una fase posterior, advertir sobre dependencies y pedir confirmación
- Citation-first: cada decisión que tomes durante el kickoff cita ARCHITECTURE.md o marathon-plan.md
