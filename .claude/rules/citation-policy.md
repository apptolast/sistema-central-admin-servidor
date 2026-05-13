# Citation Policy — anti-alucinación obligatoria

> Espejo de las 5 capas anti-alucinación de `ARCHITECTURE.md` §2.4 y la regla maestra del segundo
> cerebro de `CLAUDE.md` §"Segundo cerebro — reglas inquebrantables". Los teammates de un Agent Team
> deben leer este archivo y aplicarlo a TODO lo que produzcan que entre en `docs/` o que sea respuesta
> de un endpoint del RAG.

---

## Las 5 capas (defensa en profundidad)

### Capa 1 — Markdown en git = ÚNICA fuente de verdad

- Cualquier afirmación factual sobre la infraestructura, servicios, decisiones del proyecto, o procedimientos vive en `docs/**.md` versionado.
- `pgvector` es **índice de búsqueda**, no source-of-truth.
- Si la documentación dice X y la realidad muestra Y, **la documentación está desactualizada** — abrir PR de update; no inventar reconciliación.

### Capa 2 — LLM nunca tiene los datos memorizados

- El modelo recupera bajo demanda chunks reales del pgvector cuando responde.
- El `rag-query-service` (Fase 3) garantiza esto: arma el prompt con `system: + retrieved_chunks: + user_question:`.
- Si el modelo intenta responder sin chunks (por ejemplo, en una sesión interactiva donde Pablo pregunta directamente), debe declarar explícitamente "**Sin verificación contra el repo**: respuesta basada en conocimiento general...".

### Capa 3 — Toda respuesta DEBE incluir citas

Formatos válidos:

**Cita estática** (archivo en git):
```
[source: docs/services/data/postgres-instances.md#n8n@a1b2c3d]
[source: ARCHITECTURE.md#2.4@HEAD]
```

**Cita live** (snapshot del cluster, Fase 3+):
```
[source: live:inventory@2026-05-13T18:00:00Z]
[source: live:pod/n8n/postgres-n8n-0@2026-05-13T21:10:00Z]
```

Cada afirmación factual lleva al menos UNA cita después.

### Capa 4 — Citation validator middleware (HTTP 422)

El `rag-query-service` (Fase 3) tiene un middleware Kotlin que:
1. Parsea cada respuesta antes de devolverla al cliente
2. Extrae todas las citas con regex
3. Para cada cita estática: verifica que `<path>` existe, `<sha>` está en `git log`, `<anchor>` aparece en el archivo en ese sha
4. Para cada cita live: verifica que el snapshot existe en `live_documents` y no está stale
5. Si **cualquier cita falla** → la respuesta es rechazada con HTTP 422 + log de la violación + retry sin esa respuesta

Skill que implementa esto: `.claude/skills/rag-cite-or-die/SKILL.md`.

### Capa 5 — Score threshold 0.6

Si los top-K chunks recuperados tienen similitud cosine < 0.6:
- La respuesta es exactamente: **"No encuentro evidencia documentada sobre eso."**
- No se intenta responder con conocimiento general del modelo.
- Se loguea como `low_confidence_query` para tuning futuro de docs (puede ser que la pregunta sea buena pero los docs no la cubran).

---

## Qué se considera "afirmación factual"

Necesita cita cualquier statement que:
- Identifica un recurso del cluster (pod, service, namespace, secret, cronjob)
- Cita una versión de software (Spring Boot 3.5.4, Kotlin 2.3.21)
- Describe un comportamiento de un componente (qué hace el cronjob `cluster-self-healing`)
- Afirma un estado actual (cuántos pods corren, qué namespaces existen)
- Cita una decisión arquitectónica (por qué Modulith vs microservicios)
- Refiere un valor de configuración (`backup-target` de Longhorn = `s3://...`)

NO necesita cita:
- Explicaciones de conceptos generales del ecosistema (qué es Spring Modulith en general)
- Opiniones del autor claramente marcadas como tal ("creo que...", "según mi experiencia...")
- Comandos shell didácticos que ilustran sintaxis sin aplicarse al estado del proyecto

---

## Excepciones permitidas

- **Borradores en `docs/_drafts/**`**: se permiten afirmaciones sin cita pero con marca `STATUS: draft` en el frontmatter. La routine `citation-validator-sweep` los excluye.
- **Reportes históricos en `docs/security/reviews/YYYY-MM-DD-*.md`**: las "findings" son observaciones del reviewer, no hechos del repo — el reviewer firma el reporte y eso vale como cita implícita.
- **READMEs por módulo (`platform/<module>/README.md`)**: si el frontmatter dice `type: module-overview`, se permite descripción funcional sin citas a granularidad de bullet point, pero CADA versión de software o feature flag debe seguir teniendo cita.

---

## Cómo el `code-reviewer` valida esta política

En cada review:
1. Si el PR toca `docs/`, ejecuta `.claude/skills/rag-cite-or-die/check.sh` sobre los archivos diff
2. Si encuentra:
   - Citas a paths inexistentes → 🔴 BLOCK
   - Citas con sha que no está en `git log` → 🔴 BLOCK (alucinación pura)
   - Statements factuales sin cita → 🟡 SHOULD
   - Citas a anchors no encontrados pero path+sha OK → 💡 NIT

---

## Cómo el `architect` aplica esta política a ADRs

Los ADRs son inmutables una vez merged. La citation policy aplica al body del ADR:
- La sección "Decision" puede ser declarativa sin cita (es la decisión misma)
- La sección "Context" debe citar todas las fuentes factuales (datos del cluster, benchmarks externos, decisiones previas)
- La sección "Consequences" puede mezclar predicciones (sin cita) con references a docs existentes (con cita)

---

## Penalización por violación

- Primera violación detectada por `nightly-arch-review` → issue P3 abierto contra el author
- Segunda violación del mismo author en 30 días → escalación al team-lead, posible bloqueo de mergear PRs hasta que pase un workshop de "cómo citar bien" (informal)
- Violación que llega a producción del RAG (capa 4 falló) → bug P0 inmediato + post-mortem en `docs/postmortems/`
