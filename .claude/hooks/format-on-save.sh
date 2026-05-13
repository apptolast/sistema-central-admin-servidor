#!/usr/bin/env bash
# ============================================================================
# Hook: PostToolUse (matcher: Write|Edit|MultiEdit)
# Auto-format del archivo modificado según su extensión.
# Errors silenciados (best-effort) — el hook no debe romper el flow del agente.
# ============================================================================

set -uo pipefail

INPUT=$(cat || true)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty' 2>/dev/null || true)

if [ -z "$FILE_PATH" ] || [ ! -f "$FILE_PATH" ]; then
  exit 0
fi

EXTENSION="${FILE_PATH##*.}"

case "$EXTENSION" in
  kt|kts)
    # ktlint via gradle si disponible (más lento pero coherente con CI)
    if [ -f "./gradlew" ]; then
      ./gradlew ktlintFormat --no-daemon -q -PfilesToFormat="$FILE_PATH" 2>/dev/null || true
    elif command -v ktlint &> /dev/null; then
      ktlint --format "$FILE_PATH" 2>/dev/null || true
    fi
    ;;
  java)
    # google-java-format si disponible
    if command -v google-java-format &> /dev/null; then
      google-java-format -i "$FILE_PATH" 2>/dev/null || true
    fi
    ;;
  json)
    # jq formatter
    if command -v jq &> /dev/null; then
      tmpfile=$(mktemp)
      jq . "$FILE_PATH" > "$tmpfile" 2>/dev/null && mv "$tmpfile" "$FILE_PATH" || rm -f "$tmpfile"
    fi
    ;;
  yml|yaml)
    # yq formatter si disponible
    if command -v yq &> /dev/null; then
      yq -i '.' "$FILE_PATH" 2>/dev/null || true
    fi
    ;;
  md)
    # prettier para markdown si disponible
    if command -v prettier &> /dev/null; then
      prettier --write --parser markdown "$FILE_PATH" 2>/dev/null || true
    fi
    ;;
  sh)
    # shfmt si disponible
    if command -v shfmt &> /dev/null; then
      shfmt -w -i 2 "$FILE_PATH" 2>/dev/null || true
    fi
    ;;
  *)
    # extensión no manejada — no hacer nada
    ;;
esac

exit 0
