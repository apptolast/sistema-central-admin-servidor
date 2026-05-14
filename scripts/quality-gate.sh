#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

run_in() {
  local label="$1"
  local dir="$2"
  shift 2

  printf '\n==> %s\n' "$label"
  (cd "$ROOT/$dir" && "$@")
}

printf 'Quality gate root: %s\n' "$ROOT"

run_in "platform: check" "platform" ./gradlew check
run_in "cluster-watcher: build" "services/cluster-watcher" ../../platform/gradlew build
run_in "rag-ingestor: build" "services/rag-ingestor" ../../platform/gradlew build
run_in "rag-query: build" "services/rag-query" ../../platform/gradlew build
run_in "frontend: wasm distribution" "frontend" ../platform/gradlew compileKotlinWasmJs wasmJsBrowserDistribution

printf '\n==> helm: lint + template\n'
if ! command -v helm >/dev/null 2>&1; then
  echo "helm binary is required for the quality gate" >&2
  exit 1
fi

for chart in platform cluster-watcher keycloak rag-ingestor rag-query frontend; do
  helm lint "$ROOT/k8s/helm/$chart"
  helm template "$chart" "$ROOT/k8s/helm/$chart" >/dev/null
done
helm template platform "$ROOT/k8s/helm/platform" -f "$ROOT/k8s/helm/platform/values-dev.yaml" >/dev/null

printf '\n==> actionable marker scan\n'
if rg -n \
  --glob '*.kt' \
  --glob '*.kts' \
  --glob '*.java' \
  --glob '*.yaml' \
  --glob '*.yml' \
  --glob '!**/build/**' \
  --glob '!**/.gradle/**' \
  --glob '!**/.kotlin/**' \
  --glob '!frontend/kotlin-js-store/**' \
  '@Disabled|\bTODO\b|FIXME|\bXXX\b|HACK|NotImplemented|not yet implemented' \
  "$ROOT/.github" "$ROOT/platform" "$ROOT/services" "$ROOT/frontend" "$ROOT/k8s"; then
  echo "Actionable markers found. Resolve them or move the decision to a tracked issue/runbook." >&2
  exit 1
fi

printf '\n==> skipped test scan\n'
mapfile -d '' test_xml < <(
  find "$ROOT/platform" "$ROOT/services" \
    -path '*/build/test-results/test/*.xml' \
    -type f \
    -print0
)
if ((${#test_xml[@]} > 0)); then
  if rg -n 'skipped="[1-9]' "${test_xml[@]}"; then
    echo "Skipped tests found. Re-enable, delete, or replace them with deterministic coverage." >&2
    exit 1
  fi
fi

printf '\nQuality gate passed.\n'
