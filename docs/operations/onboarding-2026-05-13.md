---
title: "Onboarding marathon 2026-05-13 — qué hay, cómo se usa, qué falta"
type: policy
owner: pablo
source-of-truth: "git log --since=2026-05-12 --oneline + ./gradlew check"
last-verified: 2026-05-13
tags: [onboarding, marathon, idp]
audience: [pablo, claude-sessions, oncall]
---

# Onboarding marathon — qué hay después del 2026-05-13

> Single source of truth para arrancar la próxima sesión (humano o LLM). Si no entiendes algo, esta página no contesta — abre el doc citado abajo.

## 1. Estado del repo en una pantalla

| Bloque | Estado | Cómo verificar |
|--------|--------|----------------|
| Spring Modulith monolito | 6 módulos activos: `inventory`, `observability`, `secrets`, `automation`, `knowledge`, `identity` | `grep '^include' platform/settings.gradle.kts` |
| inventory module (Fase 1) | Production-ready: hexagonal, REST, JPA, Flyway, ArchUnit | `./gradlew :inventory:test` |
| identity module (Fase 4 first half) | Skeleton + Authorization domain + 7 tests | `./gradlew :identity:test` |
| Otros módulos (Fase 2-5) | Skeleton con `package-info.java + @ApplicationModule + ArchUnit` — sin lógica de negocio | `find platform/<mod>/src -type f \| wc -l` |
| `services/cluster-watcher` | Fabric8 informers + HTTP publisher | `./gradlew build` desde `services/cluster-watcher` |
| `services/rag-ingestor` | Git poll + chunking — embeddings TODO | `./gradlew test` desde `services/rag-ingestor` |
| `services/rag-query` | Cierra loop knowledge: HTTP query + pgvector adapter + LOW_NO_EVIDENCE fallback | `./gradlew test` desde `services/rag-query` |
| `frontend/composeApp/` | Compose MP Web (wasmJs) Material 3, 6 pantallas mock | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| `k8s/helm/platform/`, `cluster-watcher/`, `rag-query/` | Charts mínimos válidos (helm lint ok) | `helm lint k8s/helm/<chart>` |
| `k8s/hardening/` | NetworkPolicy NodePorts, Longhorn BackupTarget, CronJob pgdump template | `kubectl apply -f --dry-run=client` |
| CI | `.github/workflows/ci.yml` con modulith-verification + cluster-watcher-build + docker-hub mirror | `gh workflow view ci.yml` |
| Routines cloud | 7 YAMLs en `routines/` — **PENDIENTE de creación en claude.ai/code/routines** | claude.ai/code/routines |

## 2. Cómo correr cosas

```bash
# Monolito (todos los tests)
cd platform/
./gradlew check                    # 23 inventory + 7 identity + arch tests

# Identity sólo
./gradlew :identity:test

# Inventory sólo
./gradlew :inventory:test

# rag-query (requiere stage del wrapper local — CI lo hace solo)
cd services/rag-query/
cp -r ../../platform/gradle . && cp ../../platform/gradlew . && chmod +x gradlew
./gradlew test

# cluster-watcher
cd services/cluster-watcher/
./gradlew test

# Frontend dev server (Compose MP Web wasmJs)
cd frontend/
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
# → http://localhost:8080
```

## 3. Endpoints expuestos en runtime

| Servicio | Puerto interno | Path destacado |
|----------|----------------|-----------------|
| platform-app | 8080 | `/api/v1/inventory/pods`, `/api/v1/internal/inventory/ingest`, `/actuator/health` |
| cluster-watcher | 8081 | `/actuator/health` (observador) |
| rag-query | 8082 | `POST /api/v1/rag/query` `{question, topK?}` -> `{status, body, chunks, citations, warning}`, `/actuator/health` |

## 4. Lo que falta — leer estos primero

| Documento | Por qué |
|-----------|---------|
| `docs/marathon-plan.md` | Roadmap completo Fases 3-8 con waves YAML que la routine `phase-orchestrator` parsea |
| `docs/operations/agent-teams-runbook.md` | Cómo lanzar las waves manualmente si la routine no está creada |
| `docs/operations/wave-A-prompts.md`, `wave-B-prompts.md` | Prompts copy-paste para los agents |
| `docs/adrs/0005-nats-jetstream-vs-inmemory-bus.md` | Decisión bus → cuándo migrar de in-memory HTTP a NATS |
| `docs/adrs/0006-otel-collector-shape.md` | Shape OTEL para Fase 2 |
| `cluster-ops/audit/REPORT_2026-05-03.md` | Estado real del cluster Hetzner (126 pods, 36 ns, 27 runbooks) |
| `k8s/hardening/README.md` | Mapeo dolor → archivo + runbook |

## 5. Decisiones abiertas (heredadas)

1. **LLM provider RAG** — OpenAI vs Ollama vs Claude API. Defecto actual: OpenAI `text-embedding-3-small` (1536 dims, COSINE). Decisión final con coste medido en Fase 3.
2. **Backup remoto Longhorn** — `k8s/hardening/backup/longhorn-backup-target-hetzner-storage-box.yaml` deja **placeholder**. Operador tiene que crear el Storage Box en console.hetzner.com y rellenar el Secret manualmente (NO committear creds).
3. **Cierre NodePorts** — `k8s/hardening/networkpolicies/` no se ha aplicado todavía. Probar primero en staging — puede romper clientes IoT externos si no se mapean bien sus IPs.
4. **n8n-prod Helm failed** — sin tocar. Ver runbook `cluster-ops/audit/RUNBOOKS/RB-25_routine_failed.md`.

## 6. Cómo continuar la marathon

**Opción A — sin routines (manual)**:

```bash
# Próxima sesión Claude Code en el repo:
cd /home/admin/sistema-central-admin-servidor
git pull
# Pedirle al lead que arranque la Wave C (Fase 3 knowledge-rag):
claude --prompt "$(cat docs/operations/wave-C-prompts.md 2>/dev/null || echo 'falta este wave — generarlo desde docs/marathon-plan.md§waves[id=C]')"
```

**Opción B — routines cloud**:

```bash
# Crear las 4 routines nuevas en claude.ai/code/routines apuntando al repo:
#   phase-orchestrator (cron 0 6 * * *)
#   runbook-migrator (cron 0 2 * * 2,5)
#   citation-validator-sweep (cron 0 4 * * 0)
#   dependency-update-radar (cron 0 6 * * 0)
# Plantillas en routines/*.yaml
```

## 7. Coste y consumo

- Marathon hoy: ~6 commits, ~150 archivos creados, ~14k líneas netas.
- Sigue siendo válido el cap de la maratón inicial (ver `docs/marathon-plan.md` §"Riesgos: Coste tokens").
- Recomendado: revisar `claude.ai/settings/usage` antes de cada nueva wave.

## 8. Atajos útiles

```bash
# Buscar runbooks que cubren un síntoma
ls /home/admin/cluster-ops/audit/RUNBOOKS/ | grep -i <symptom>

# Ver waves pendientes
yq '.waves[] | select(.status != "done") | .id + " — " + .phase' docs/marathon-plan.md

# Helm lint todos los charts
for c in k8s/helm/*/; do helm lint "$c"; done

# Ejecutar todo el test suite a la vez
( cd platform && ./gradlew check ) && \
( cd services/cluster-watcher && ./gradlew test ) && \
( cd services/rag-query && ./gradlew test )
```

## 9. Contacto

Pablo Hurtado — pablohurtadohg@gmail.com — único maintainer del repo.

---

> Este documento se actualiza al final de cada marathon. Si en la próxima sesión esto está obsoleto, regenerar con un nuevo onboarding-`YYYY-MM-DD`.md y dejar éste como histórico.
