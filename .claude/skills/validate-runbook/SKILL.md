---
name: validate-runbook
description: >
  Valida que un runbook en docs/runbooks/ cumple las reglas de calidad del proyecto: frontmatter
  completo según docs/_template.md, last-verified < 90 días, source-of-truth referenciado, comandos
  shell sintácticamente válidos, y referencias cruzadas a otros docs resolvibles. Útil para
  pre-commit local y para la routine runbook-migrator que migra los 27 runbooks de cluster-ops/audit/.
---

# Skill: validate-runbook

Verifica que un runbook markdown cumple las cinco reglas de calidad del segundo cerebro.

## Cuándo usar

- Tras crear o editar cualquier archivo en `docs/runbooks/**`
- Antes de mergear un PR que toca runbooks
- En la routine `runbook-migrator` después de migrar cada batch de 4 runbooks desde `cluster-ops/audit/RUNBOOKS/`
- A demanda del usuario con `/cite-or-die-check` (ver `.claude/commands/`)

## Checklist (todas obligatorias)

### 1. Frontmatter completo

El runbook DEBE empezar con `---` y contener al menos:

```yaml
---
title: "..."
type: runbook               # OBLIGATORIO valor "runbook"
owner: <github-username>
source-of-truth: "<comando|url>"  # OBLIGATORIO no vacío
last-verified: YYYY-MM-DD   # OBLIGATORIO ISO 8601
tags: [...]
status: stable              # uno de: stable | beta | deprecated | superseded
depends-on: [...]           # tipos: service, namespace, pvc, cronjob, ingress, dns
related-runbooks: [...]
---
```

### 2. last-verified reciente

Si `last-verified` está a más de 90 días de hoy, marcar como **STALE** y devolver código 1.

```bash
last_verified=$(yq '.last-verified' "$file")
diff_days=$(( ($(date -d today +%s) - $(date -d "$last_verified" +%s)) / 86400 ))
[ "$diff_days" -gt 90 ] && echo "STALE: $file last-verified $last_verified ($diff_days days)" && exit 1
```

### 3. source-of-truth ejecutable

Si `source-of-truth` empieza con `kubectl`, `helm`, `curl`, debe ser sintácticamente válido (parseable). NO se ejecuta — sólo se valida la forma.

```bash
sot=$(yq '.source-of-truth' "$file")
# Validación mínima: si parece comando shell, probarlo con shellcheck o bash -n
echo "$sot" | grep -qE '^(kubectl|helm|curl|psql|systemctl)' && \
    bash -nc "$sot" 2>/dev/null || echo "WARN: source-of-truth no parseable como shell: $sot"
```

### 4. Comandos shell en el cuerpo bien formados

Cada bloque ` ```bash ` o ` ```shell ` del runbook debe pasar `bash -n` (sintaxis check, no ejecución).

```bash
awk '/^```bash$/,/^```$/' "$file" | grep -v '^```' | bash -n
```

### 5. Referencias cruzadas resolvibles

- Cada `related-runbooks: [RB-XX]` debe existir como `docs/runbooks/RB-XX-*.md`
- Cada link `[texto](path)` con path relativo debe resolver a un archivo existente
- Cada `[source: docs/X/Y.md#section@sha]` debe tener un sha real (verificar con `git log`)

```bash
# Cross-ref runbooks
yq '.related-runbooks[]' "$file" | while read rb; do
  test -f "docs/runbooks/$rb"*.md || echo "BROKEN-XREF: $rb"
done

# Markdown links a archivos relativos
grep -oE '\[.*\]\(([^http][^)]+)\)' "$file" | \
  grep -oE '\(([^)]+)\)' | tr -d '()' | while read p; do
    test -e "$p" || echo "BROKEN-LINK: $p"
  done
```

## Output

```
✓ docs/runbooks/RB-01-host-disk.md       OK (last-verified 2026-05-10, 3 days)
✗ docs/runbooks/RB-13-pg-txid.md         STALE (last-verified 2025-12-01, 163 days)
✗ docs/runbooks/RB-22-wg-handshake.md    BROKEN-XREF: RB-99
─────────────────────────────────────────────────────────────────
Resumen: 25/27 OK · 2 fallos
```

Exit code: `0` si todos OK, `1` si al menos uno falla.

## Implementación referencial

Para invocar la skill desde un agente:

```bash
# Single runbook
.claude/skills/validate-runbook/check.sh docs/runbooks/RB-01-host-disk.md

# All runbooks
.claude/skills/validate-runbook/check.sh docs/runbooks/*.md
```

Script sugerido (no creado todavía, lo implementa tech-writer cuando migre los primeros runbooks):

```bash
#!/usr/bin/env bash
set -euo pipefail
exit_code=0
for file in "$@"; do
    # ... lógica de los 5 checks de arriba ...
    if [ <falla> ]; then
        exit_code=1
        echo "✗ $file ..."
    else
        echo "✓ $file OK"
    fi
done
exit $exit_code
```
