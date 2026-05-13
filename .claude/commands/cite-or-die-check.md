---
description: Ejecuta la skill rag-cite-or-die sobre docs/ — busca afirmaciones factuales sin cita y citas rotas (path/sha/anchor inválido)
allowed-tools: Read, Bash, Grep
argument-hint: [path-prefix]
---

Audita las citas en `docs/$ARGUMENTS/**` (o todo `docs/` si vacío) buscando violaciones de la
citation-policy del proyecto.

## Contexto

Ver:
- `.claude/skills/rag-cite-or-die/SKILL.md` (skill que implementa el chequeo)
- `.claude/rules/citation-policy.md` (las 5 capas anti-alucinación)
- `ARCHITECTURE.md` §2.4 (decisión arquitectónica)

## Pasos

### 1. Determinar scope

```bash
if [ -z "$ARGUMENTS" ]; then
    scope="docs/"
else
    scope="docs/$ARGUMENTS/"
fi

# Sanity: scope debe ser un directorio existente
test -d "$scope" || { echo "Error: $scope no existe"; exit 1; }
```

### 2. Listar archivos a auditar

```bash
# Excluir _template, _drafts, _live (ver citation-policy §"Excepciones")
files=$(find "$scope" -name "*.md" \
    -not -name "_template.md" \
    -not -path "*/_drafts/*" \
    -not -path "*/_live/*")

echo "Auditando $(echo "$files" | wc -l) archivos..."
```

### 3. Ejecutar la skill

Cuando esté implementada en Fase 3:
```bash
bash .claude/skills/rag-cite-or-die/check.sh $files
```

Hasta entonces, fallback inline:

```bash
broken=0
stale=0
no_cite=0

for f in $files; do
    # 3.1. Extraer citas
    citations=$(grep -oE '\[source: [^]]+\]' "$f" || true)

    # 3.2. Para cada cita estática
    while IFS= read -r c; do
        [ -z "$c" ] && continue
        if [[ "$c" =~ \[source:\ ([^#]+)#([^@]+)@(.+)\] ]]; then
            path="${BASH_REMATCH[1]}"
            anchor="${BASH_REMATCH[2]}"
            sha="${BASH_REMATCH[3]}"

            # 3.2.1. Path existe?
            if [ ! -f "$path" ]; then
                echo "🔴 BROKEN-PATH in $f: cita a $path"
                broken=$((broken+1))
                continue
            fi

            # 3.2.2. SHA existe en git?
            if [ "$sha" != "HEAD" ] && ! git cat-file -e "$sha" 2>/dev/null; then
                echo "🔴 HALLUCINATED-SHA in $f: cita a sha $sha"
                broken=$((broken+1))
                continue
            fi

            # 3.2.3. Anchor existe en el archivo? (warn no block)
            if ! git show "${sha}:${path}" 2>/dev/null | grep -qE "^#{2,3} .*${anchor}"; then
                echo "🟡 WEAK-ANCHOR in $f: '$anchor' no encontrado en $path@$sha"
            fi
        fi
    done <<< "$citations"

    # 3.3. Heurística: claims factuales sin cita
    # Buscar patrones de claim (versión X.Y, "el servicio X tiene Y", nombre de pod/namespace/cronjob)
    # que NO tienen una cita en la misma frase o párrafo
    factual_no_cite=$(grep -nE '(versión|deploy|pod|namespace|cronjob|service)[^.]{0,60}\.' "$f" | \
        grep -v '\[source:' | head -5)
    if [ -n "$factual_no_cite" ]; then
        echo "🟡 LIKELY-NO-CITE in $f:"
        echo "$factual_no_cite" | sed 's/^/    /'
        no_cite=$((no_cite+1))
    fi
done

echo "────────────────────────────────────"
echo "Total: $broken broken · $stale stale · $no_cite no-cite warnings"
```

### 4. Reportar

```
═══════════════════════════════════════════════════════════
Citation audit: docs/services/
═══════════════════════════════════════════════════════════
🔴 BROKEN-PATH in docs/services/data/postgres-instances.md
   Cita a "docs/runbooks/RB-99-fake.md" — archivo no existe

🟡 WEAK-ANCHOR in docs/runbooks/RB-10-pg-connections-high.md
   Cita a "ARCHITECTURE.md#2.4@HEAD" anchor "2.4" no encontrado (esperado "## 2.4 ..." o "### 2.4 ...")

🟡 LIKELY-NO-CITE in docs/infrastructure/hetzner-vps.md
   Línea 12: "El VPS es CCX62 con 16 vCPU."
   Línea 23: "El cluster corre kubeadm v1.32.3."
   → Necesitan cita (ej. [source: live:infrastructure/hetzner-vps@2026-05-13])
────────────────────────────────────
Resumen: 1 broken · 1 weak-anchor · 1 no-cite warning (en 1 archivo)
```

Exit code:
- `0` si no hay broken (warnings permitidos)
- `1` si al menos un broken

## Reglas

- Sólo lectura. NUNCA modificar docs.
- No reportar archivos en `_drafts/` o `_live/` ni `_template.md`.
- Si `git` no disponible (offline), reportar como SKIPPED no FAILED para validaciones de SHA.
- La heurística "LIKELY-NO-CITE" tiene falsos positivos — listar como warning, no como block.
