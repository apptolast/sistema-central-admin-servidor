#!/usr/bin/env bash
# ============================================================================
# Hook: TaskCompleted
# Ejecuta los quality gates del proyecto antes de aceptar el cierre de una tarea.
# Exit code 2 = previene la finalización + envía feedback al agente.
# ============================================================================

set -euo pipefail

INPUT=$(cat || true)
CWD=$(echo "$INPUT" | jq -r '.cwd // "."' 2>/dev/null || pwd)

cd "$CWD"

# Si la tarea no toca el módulo platform/ ni services/, no aplicar checks pesados
if [[ -n "$(git status --porcelain 2>/dev/null | grep -E '^\s*(M|A|D)\s+(platform|services|frontend)/' || true)" ]]; then
  FULL_CHECK=1
else
  FULL_CHECK=0
fi

# 1. ktlint (formato)
if [ -f "./gradlew" ]; then
  if ! ./gradlew ktlintCheck --no-daemon -q 2>&1 | tail -20; then
    echo "❌ ktlintCheck failed. Ejecuta './gradlew ktlintFormat' y vuelve a marcar la tarea como completa." >&2
    exit 2
  fi
fi

# 2. detekt (static analysis)
if [ -f "./gradlew" ] && [ "$FULL_CHECK" -eq 1 ]; then
  if ! ./gradlew detekt --no-daemon -q 2>&1 | tail -20; then
    echo "❌ detekt found issues. Corrige antes de cerrar la tarea." >&2
    exit 2
  fi
fi

# 3. Tests (unit + integration via Testcontainers)
if [ -f "./gradlew" ] && [ "$FULL_CHECK" -eq 1 ]; then
  if ! ./gradlew check --no-daemon -q 2>&1 | tail -40; then
    echo "❌ Tests fallando. Corrige los tests antes de cerrar la tarea." >&2
    exit 2
  fi
fi

# 4. ArchUnit + Spring Modulith verification
if [ -f "./gradlew" ] && [ "$FULL_CHECK" -eq 1 ]; then
  if ! ./gradlew :platform-app:test --tests "*ModulithTest*" --no-daemon -q 2>&1 | tail -20; then
    echo "❌ Spring Modulith / ArchUnit verification failed. Revisa los boundaries." >&2
    exit 2
  fi
fi

echo "✅ Quality gates passed"
exit 0
