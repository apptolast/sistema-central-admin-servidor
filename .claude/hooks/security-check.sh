#!/usr/bin/env bash
# ============================================================================
# Hook: PreToolUse (matcher: Write|Edit)
# Bloquea la escritura de archivos sensibles y detecta secrets en el contenido.
# Exit code 2 = bloquea la operación + envía feedback.
# ============================================================================

set -euo pipefail

INPUT=$(cat || true)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty' 2>/dev/null || true)
CONTENT=$(echo "$INPUT" | jq -r '.tool_input.content // .tool_input.new_string // empty' 2>/dev/null || true)

# 1. Bloquear escritura en archivos sensibles (paths)
BLOCKED_PATTERNS=(
  "\.env$"
  "\.env\."
  "/secrets/"
  "\.key$"
  "\.pem$"
  "\.p12$"
  "\.jks$"
  "\.keystore$"
  "id_rsa"
  "credentials\.json$"
  "kubeconfig"
)

for pattern in "${BLOCKED_PATTERNS[@]}"; do
  if [[ "$FILE_PATH" =~ $pattern ]]; then
    echo "🔒 Escritura bloqueada: '$FILE_PATH' coincide con patrón sensible '$pattern'." >&2
    echo "   Si necesitas un .env de ejemplo, usa '.env.example' con valores dummy." >&2
    exit 2
  fi
done

# 2. Detectar secrets reales en el contenido (heurísticas comunes)
if [ -n "$CONTENT" ]; then
  # API key / token / password patterns con valor literal de longitud sospechosa
  if echo "$CONTENT" | grep -qiE '(api[_-]?key|secret[_-]?key|password|token|access[_-]?key)["'"'"']?\s*[:=]\s*["'"'"'][A-Za-z0-9+/=_-]{20,}["'"'"']'; then
    echo "🔒 Posible secret literal detectado en el contenido." >&2
    echo "   Usa variables de entorno (@Value, @ConfigurationProperties) o Spring profile externalization." >&2
    exit 2
  fi

  # Private key blocks
  if echo "$CONTENT" | grep -qE '(BEGIN RSA PRIVATE KEY|BEGIN OPENSSH PRIVATE KEY|BEGIN EC PRIVATE KEY|BEGIN PRIVATE KEY)'; then
    echo "🔒 Private key detectada en el contenido. No commitear." >&2
    exit 2
  fi

  # AWS access key
  if echo "$CONTENT" | grep -qE 'AKIA[0-9A-Z]{16}'; then
    echo "🔒 AWS Access Key detectada." >&2
    exit 2
  fi

  # JWT-like (header.payload.signature en base64)
  if echo "$CONTENT" | grep -qE 'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}'; then
    echo "🔒 Posible JWT literal en el contenido. Si es para test, usa un placeholder." >&2
    exit 2
  fi

  # Docker Hub token (subido como repository secret 2026-05-13)
  if echo "$CONTENT" | grep -qE 'DOCKERHUB_TOKEN\s*[:=]\s*["'"'"']?[a-zA-Z0-9]{20,}'; then
    echo "🔒 DOCKERHUB_TOKEN literal detectado. Usa \${{ secrets.DOCKERHUB_TOKEN }} en workflows." >&2
    exit 2
  fi

  # GitHub Personal Access Tokens (ghp_, gho_, ghs_, ghr_)
  if echo "$CONTENT" | grep -qE 'gh[psor]_[A-Za-z0-9]{30,}'; then
    echo "🔒 GitHub Personal Access Token detectado en el contenido." >&2
    exit 2
  fi
fi

exit 0
