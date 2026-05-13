#!/usr/bin/env bash
# ============================================================================
# Hook: Stop
# Bloquea que la sesión termine si hay tasks PENDING o IN_PROGRESS sin owner
# en la wave activa. Exit code 2 = previene el stop + envía feedback al agente.
# ============================================================================

set -uo pipefail

INPUT=$(cat || true)

# Detectar si hay team activo
TEAM_DIR=$(ls -td "$HOME"/.claude/teams/*/ 2>/dev/null | head -1)
if [ -z "$TEAM_DIR" ] || [ ! -d "$TEAM_DIR" ]; then
    # No hay team — permitir stop
    exit 0
fi

TEAM_NAME=$(basename "$TEAM_DIR")
TASK_DIR="$HOME/.claude/tasks/$TEAM_NAME"

if [ ! -d "$TASK_DIR" ]; then
    # Team sin tasks (smoke test o similar) — permitir stop
    exit 0
fi

# Contar tasks abiertas
pending_count=0
in_progress_count=0
stuck=()

for f in "$TASK_DIR"/*.json; do
    [ -f "$f" ] || continue
    status=$(jq -r '.status // empty' "$f" 2>/dev/null || echo "")
    owner=$(jq -r '.owner // empty' "$f" 2>/dev/null || echo "")
    subject=$(jq -r '.subject // empty' "$f" 2>/dev/null || echo "")
    task_id=$(jq -r '.id // empty' "$f" 2>/dev/null || echo "?")

    case "$status" in
        pending)
            pending_count=$((pending_count+1))
            stuck+=("- [$task_id] PENDING (no owner): $subject")
            ;;
        in_progress)
            in_progress_count=$((in_progress_count+1))
            stuck+=("- [$task_id] IN_PROGRESS (owner: $owner): $subject")
            ;;
    esac
done

if [ "$pending_count" -eq 0 ] && [ "$in_progress_count" -eq 0 ]; then
    # Todas las tasks completed — permitir stop
    exit 0
fi

# Hay tasks abiertas — bloquear stop
cat <<EOF >&2
🛑 Stop bloqueado: hay tasks abiertas en el team "$TEAM_NAME".

PENDING: $pending_count   IN_PROGRESS: $in_progress_count

$(printf '%s\n' "${stuck[@]}")

Opciones antes de parar:
  1. Esperar a que los teammates completen sus tasks.
  2. Reasignar tasks pending: TaskUpdate <id> con owner.
  3. Si abandonas la wave intencionalmente: cleanup team primero.
     (Pide al team-lead: "Clean up the team")

Si confirmas que quieres parar igual: invocar Stop con --force (no implementado;
manualmente borra ~/.claude/tasks/$TEAM_NAME/ si entiendes la consecuencia).
EOF

exit 2
