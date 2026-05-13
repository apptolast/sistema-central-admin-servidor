# Ownership matrix — quién edita qué

> Espejo del bloque "Ownership de archivos" de `CLAUDE.md`. Replicado aquí para que cada teammate
> de un Agent Team lo lea como rule directamente, sin depender de que el lead lo cite.
> Si esto y CLAUDE.md divergen, **CLAUDE.md gana** — actualizar este archivo a la versión vigente.

---

## Regla maestra

Dos agentes **NUNCA** editan el mismo archivo simultáneamente. El task claiming de Agent Teams
usa file locking sobre la task list pero NO sobre las ediciones de archivos (limitación
documentada v2026-05). La defensa es ownership estricto + scoping de prompts.

---

## Matriz por rol

| Agent role | Puede EDITAR | NO PUEDE EDITAR |
|---|---|---|
| **team-lead** | `docs/progress/YYYY-Www.md` únicamente | Todo lo demás. Coordina vía Task* + SendMessage. |
| **architect** | `docs/architecture.md`, `docs/adrs/**`, `ARCHITECTURE.md`, `platform/<module>/api/**` (sólo interfaces) | Código en `application/`, `domain/`, `infrastructure/`. Frontend. Tests. CI/CD. |
| **backend-dev** | `platform/{inventory,secrets,observability,automation,knowledge,identity}/**`, `services/{cluster-watcher,rag-ingestor}/**`, `services/rag-query/kotlin-facade/**` | `frontend/**`. `services/rag-query/python-r2r/**` (es R2R upstream). Contratos en `api/` sin aprobación del architect. `docs/`. `.claude/`. `.github/`. Tests E2E. |
| **frontend-dev** | `frontend/composeApp/**`, `frontend/Dockerfile`, `frontend/nginx.conf`, DTOs cliente en `frontend/.../data/remote/dto/**` | Backend (`platform/`, `services/`). Contratos en `platform/<module>/api/`. `docs/`. `.claude/`. `.github/`. |
| **qa-engineer** | `**/src/test/kotlin/**`, `**/src/integrationTest/kotlin/**`, `tests/e2e/**`, `tests/contracts/**`, `tests/architecture/**` | Código de producción en `platform/**`, `services/**`, `frontend/composeApp/src/<no-test>/**`. Si encuentras bug → NO arregles → crea TaskCreate. |
| **security-reviewer** | `docs/security/**`, `tests/security/**` (sólo reportes, los tests reales los hace qa-engineer) | **READ-ONLY** sobre código de producción. Reporta findings; NO parchea. |
| **devops-engineer** | `.github/workflows/**`, `k8s/**`, `Dockerfile`, `**/Dockerfile`, `docker-compose.yml`, `routines/**`, `scripts/**`, `frontend/nginx.conf`, `.env.example` | Código Kotlin de producción. `docs/` (excepto `docs/operations/`). Cluster en runtime (`kubectl delete`, `helm uninstall`). |
| **tech-writer** | `docs/**` excepto `docs/adrs/**` (architect) y `docs/security/**` (security-reviewer), `README.md` (cambios mayores), `CONTRIBUTING.md`, `CHANGELOG.md`, KDoc en código (comentarios SIN cambiar lógica) | Código Kotlin (lógica). `.claude/`. `.github/`. |
| **code-reviewer** | Comentarios en PR vía `gh pr comment` y review state vía `gh pr review` | **READ-ONLY** sobre todo el código. NO mergea (eso es del lead). |
| **mentor** | Sólo sus respuestas al usuario o vía SendMessage | **READ-ONLY** sobre todo el código. |

---

## Casos límite documentados

### Quién edita `CHANGELOG.md`?

`tech-writer` cuando es una entrada de cambio de docs. `devops-engineer` cuando es de CI/CD. Lead aprueba antes de mergear PR.

### Quién edita `platform/<module>/api/events/<X>Event.kt`?

`architect` define la interfaz inicialmente. Una vez merged, sólo se puede modificar:
- Para añadir campos opcionales → `architect` + ADR si el cambio afecta consumers
- Para breaking change → nuevo evento `<X>EventV2` y deprecar el viejo; ADR obligatorio

### Quién edita `platform/<module>/package-info.java` (@ApplicationModule)?

`architect` cuando cambia `allowedDependencies`. `backend-dev` puede crearlo inicialmente con valores acordados pero requiere review del architect antes de merge.

### Quién edita `frontend/composeApp/src/commonMain/kotlin/com/apptolast/platform/ui/theme/`?

`frontend-dev` lo mantiene. Cambios mayores (paleta de colores, escala tipográfica) requieren ADR del architect + sync con Claude Design.

### Quién edita `routines/**.yaml`?

`devops-engineer` los mantiene. `team-lead` puede crear uno nuevo si la wave actual lo requiere, pero el merge final es responsabilidad del devops.

---

## Violación de ownership: qué pasa

El `code-reviewer` detecta violaciones en su pass pre-merge:
- 🟡 **SHOULD**: un teammate WORKER editó un archivo fuera de su ownership pero el cambio es trivial (typo fix, comentario)
- 🔴 **BLOCK**: un teammate WORKER editó código de otro teammate WORKER, o un ADR sin ser architect

El `team-lead` puede aprobar manualmente una violación si:
- Es una hot-fix urgente y el owner correcto está offline → labelearlo y crear task de follow-up
- Hay racional documentada en el PR description

---

## Updates de esta matriz

Si crees que la matriz necesita cambio:
1. Plantea el cambio al `team-lead` con racional
2. Si aprobado, `architect` actualiza `CLAUDE.md` §"Ownership de archivos" PRIMERO
3. Sólo después, alguien (típicamente `tech-writer`) sincroniza este archivo
