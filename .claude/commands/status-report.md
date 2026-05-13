---
description: Generate a status report of the project — phase progress, open issues, PR backlog, drift between docs and reality
allowed-tools: Read, Bash, Grep
---

Genera un reporte completo del estado actual del proyecto sistema-central-admin-servidor.

Estructura del reporte:

## 1. Fase actual

- Leer ARCHITECTURE.md §4 (roadmap por fases)
- Identificar qué fase está activa según los commits recientes y los archivos modificados
- Estimar % de completitud de la fase actual

## 2. Commits & PRs recientes (últimos 14 días)

```bash
git log --since="14 days ago" --pretty=format:'%h | %an | %s' | head -50
gh pr list --state all --limit 20 --json number,title,state,createdAt,mergedAt
```

Agrupa por: features nuevos, fixes, refactors, docs.

## 3. Tests + CI status

```bash
# Resultado del último CI run en main
gh run list --workflow=ci.yml --branch=main --limit=5

# Coverage
find . -name "jacocoTestReport.xml" -exec head -1 {} \;
```

## 4. Estado del cluster (si tiene kubectl access)

```bash
# Comprobar si los pods del IDP están vivos
kubectl get pods -n apptolast-platform-dev 2>&1 || echo "dev not deployed yet"
kubectl get pods -n apptolast-platform-prod 2>&1 || echo "prod not deployed yet"
```

## 5. Docs drift (Fase 3+)

```bash
# Llamar al endpoint del IDP si existe
curl -s https://platform-dev.apptolast.com/api/docs/drift 2>/dev/null \
  || echo "Docs drift endpoint not deployed yet"
```

## 6. Open issues + tasks

```bash
gh issue list --state open --limit 20
gh issue list --label="security" --state open
gh issue list --label="phase-0\|phase-1\|phase-2\|phase-3" --state open
```

## 7. Risks & blockers actuales

Leer el plan en `/home/admin/.claude/plans/` (si existe el correspondiente) o reportar manualmente.

## 8. Próximos pasos

- ¿Qué bloquea el avance a la siguiente fase?
- ¿Hay tareas listas para empezar?
- ¿Hay decisiones pendientes (issues sin ADR)?

## Formato output

Markdown estructurado con secciones claras. Sin emojis salvo en CHANGELOG-style headers (✨ feature, 🐛 fix). Tablas para listas de PRs/issues. Concluir con un resumen ejecutivo de 3-5 bullets.
