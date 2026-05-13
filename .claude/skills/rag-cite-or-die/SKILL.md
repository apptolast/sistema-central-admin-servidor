---
name: rag-cite-or-die
description: >
  Verifica que cada afirmación factual en docs/**.md tiene una cita resolvible en formato
  [source: path/file.md#section@commitsha] o [source: live:resource@timestamp]. Bloquea respuestas
  con citas inventadas (sha no existe en git log, path no existe). Implementa la capa 3 de defensa
  anti-alucinación definida en ARCHITECTURE.md §2.4.
---

# Skill: rag-cite-or-die

Implementa la regla inquebrantable #3 del segundo cerebro: **toda respuesta del RAG y todo doc factual DEBE incluir citas resolvibles**.

Ver `CLAUDE.md` §"Segundo cerebro — reglas inquebrantables" y `ARCHITECTURE.md` §2.4.

## Cuándo usar

- En el `rag-query-service` (futuro Fase 3) como middleware sobre toda respuesta antes de devolverla
- En el `citation-validator-sweep` routine semanal sobre todo `docs/**.md`
- A demanda del usuario con `/cite-or-die-check` (slash command)
- Al revisar un PR que toca documentación factual

## Las dos formas de cita válidas

### 1. Cita estática (archivo en git)

Formato: `[source: <path>#<anchor>@<commit-sha>]`

Ejemplos:
```
[source: docs/services/data/postgres-instances.md#n8n@a1b2c3d]
[source: docs/runbooks/RB-10-pg-connections-high.md#diagnosis@7f8e9d2]
[source: ARCHITECTURE.md#2.4@HEAD]
```

Validaciones:
- `<path>` debe existir relativo al root del repo
- `<commit-sha>` debe existir en `git log` (al menos los primeros 7 chars)
- `<anchor>` debe corresponder a un `## <section>` o `### <section>` del archivo en ese commit
- Si `<anchor>` no aparece textualmente, intentar slug match (lowercase + dashes)

Comando de verificación:
```bash
# Path exists?
test -f "$path" || echo "BROKEN: path $path"

# SHA exists?
git cat-file -e "$sha" 2>/dev/null || echo "BROKEN: sha $sha not in git log"

# Anchor exists in that SHA?
git show "$sha:$path" 2>/dev/null | grep -qE "^#{2,3} .*$anchor" || \
    echo "WARN: anchor $anchor not found in $path@$sha"
```

### 2. Cita live (snapshot vivo del cluster)

Formato: `[source: live:<resource>@<ISO-timestamp>]`

Ejemplos:
```
[source: live:inventory@2026-05-13T18:00:00Z]
[source: live:pod/n8n/postgres-n8n-0@2026-05-13T21:10:00Z]
[source: live:metric/cluster.cpu.usage@2026-05-13T21:00:00Z]
```

Validaciones:
- `<resource>` debe existir en la tabla `live_documents` de pgvector (cuando el rag-ingestor esté operativo, Fase 3)
- `<timestamp>` debe estar en el TTL configurado (default 24h — snapshots más viejos se consideran stale)

Hasta Fase 3, las citas `live:` se rechazan con `NOT_YET_IMPLEMENTED`.

## Detección de citas inventadas (anti-alucinación core)

El validador es **estricto por diseño**:

```
Si una respuesta contiene una afirmación factual (claim) sin una cita después
  → REJECT con HTTP 422 "missing citation for claim '<excerpt>'"

Si una cita usa formato válido pero el path/sha/anchor no resuelve
  → REJECT con HTTP 422 "hallucinated citation: <citation>"

Si una cita es plausible-pero-falsa (sha existe pero el contenido no menciona el claim)
  → WARN (no bloquea pero logueado para tuning del modelo)
```

## Comando de invocación

```bash
# Validar todo docs/
.claude/skills/rag-cite-or-die/check.sh docs/

# Validar un solo archivo
.claude/skills/rag-cite-or-die/check.sh docs/services/data/postgres-instances.md

# Validar una respuesta RAG (stdin)
echo "El Postgres de n8n usa la versión 16.10 [source: docs/...]" | \
    .claude/skills/rag-cite-or-die/check.sh -
```

Exit codes:
- `0` — todas las citas válidas
- `1` — al menos una cita rota o claim sin cita
- `2` — formato del input inválido (no se pudo parsear citas)

## Las 5 capas anti-alucinación (memoria operativa)

De `ARCHITECTURE.md` §2.4 y `feedback_rag_anti_hallucination.md`:

1. **Markdown en git** = ÚNICA fuente de verdad
2. **LLM nunca tiene datos memorizados** — los recupera bajo demanda de chunks reales
3. **Toda respuesta DEBE incluir citas** (esta skill enforces capa 3)
4. **Citation validator middleware** parsea respuestas y rechaza citas inventadas
5. **Score threshold 0.6**: si top-K chunks tienen similitud < 0.6 → "no encuentro evidencia documentada"

Esta skill cubre **capa 3 y 4**. Las capas 1, 2, 5 viven en el `rag-ingestor` y `rag-query-service` (Fase 3).

## Implementación referencial

Script principal a implementar en Fase 3:

```bash
#!/usr/bin/env bash
# .claude/skills/rag-cite-or-die/check.sh
set -euo pipefail
files=("$@")
exit_code=0
for file in "${files[@]}"; do
    # 1. Extraer todas las citas con regex
    citations=$(grep -oE '\[source: [^]]+\]' "$file")
    while read citation; do
        # 2. Parsear formato
        if [[ "$citation" =~ \[source:\ ([^#]+)#([^@]+)@(.+)\] ]]; then
            path="${BASH_REMATCH[1]}"
            anchor="${BASH_REMATCH[2]}"
            sha="${BASH_REMATCH[3]}"
            # 3. Validar (ver más arriba)
            # 4. exit_code=1 si rota
        fi
    done <<< "$citations"
done
exit $exit_code
```

## Configuración como user-invocable

Esta skill tiene en frontmatter `user-invocable: true` (implícito por convención del proyecto). Pablo puede invocarla con:

```
/cite-or-die-check docs/
```

(Ver `.claude/commands/cite-or-die-check.md`.)
