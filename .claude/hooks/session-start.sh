#!/usr/bin/env bash
# ============================================================================
# Hook: SessionStart
# Mensaje informativo al inicio de cada sesión Claude Code en este repo.
# Recordatorio de marathon plan, fase activa, y comandos útiles.
# ============================================================================

set -uo pipefail

# Detectar fase activa (heurística rápida: el último commit fase-X)
PHASE=$(cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null && git log -10 --pretty=format:'%s' 2>/dev/null | \
    grep -oE 'phase-[0-9]+' | head -1 | grep -oE '[0-9]+' || echo "?")

# Detectar branch
BRANCH=$(cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null && git branch --show-current 2>/dev/null || echo "?")

# Detectar wave activa
TEAM_DIR=$(ls -td "$HOME"/.claude/teams/*/ 2>/dev/null | head -1)
TEAM_NAME=$(basename "$TEAM_DIR" 2>/dev/null || echo "—")

cat <<EOF >&2
═══════════════════════════════════════════════════════════════════
  sistema-central-admin-servidor — Internal Developer Platform
  Hoy: $(date -u +%Y-%m-%d)
═══════════════════════════════════════════════════════════════════
  Branch:        $BRANCH
  Fase activa:   $PHASE  (de 0..7, ver ARCHITECTURE.md §4)
  Wave team:     $TEAM_NAME
───────────────────────────────────────────────────────────────────
  Docs maestras:
    CLAUDE.md, ARCHITECTURE.md, docs/marathon-plan.md
  Slash commands útiles:
    /phase-kickoff <N>     — arranca wave de Fase N
    /wave-status           — snapshot del team activo
    /status-report         — vista de proyecto completo
    /cite-or-die-check     — auditar citas en docs/
    /new-module <name>     — scaffold nuevo bounded context
    /new-microservice <n>  — scaffold servicio extraído (con ADR)
───────────────────────────────────────────────────────────────────
  Reglas inquebrantables (CLAUDE.md §"Segundo cerebro"):
    1. Markdown en git = única fuente de verdad
    2. Toda afirmación factual necesita cita [source: path#section@sha]
    3. Sin evidencia → "no encuentro evidencia documentada"
    4. Citas inventadas son un BUG → HTTP 422
───────────────────────────────────────────────────────────────────
EOF

exit 0
