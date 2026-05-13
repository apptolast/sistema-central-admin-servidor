# CLAUDE.md — Contexto maestro del proyecto

> Este archivo lo lee Claude Code automáticamente al abrir el repo.
> Es la fuente de verdad de **qué es este proyecto, cómo se organiza, y qué reglas seguir**.

---

## Qué es esto

**Sistema Central de Administración del Servidor** — la Internal Developer Platform (IDP) de AppToLast.

Es la **capa de control** sobre un cluster Kubernetes single-node + un VPS Hetzner CPX62. Unifica inventario, observabilidad, secrets, automatización, identidad, y un **segundo cerebro RAG anti-alucinación** sobre toda la infraestructura.

Single-tenant. Para AppToLast. Mantenible por una persona (Pablo).

---

## Stack y versiones (todas fijadas en `platform/gradle/libs.versions.toml`)

- Kotlin 2.3.21 · JDK 21 LTS · Spring Boot 3.5.x · Spring Modulith 2.0 GA
- fabric8-kubernetes-client 7.x · Spring Data JPA + Flyway 10.x · PostgreSQL 16 + pgvector
- NATS JetStream 2.10+ · gRPC-Kotlin 1.4+
- Compose Multiplatform Web 1.10.2 (Wasm target) · Koin 4.2 · Ktor 3.4 · Material 3
- Keycloak 26.6 · Traefik 3.3.6 (ya desplegado) · Keel 0.19 (ya desplegado)
- R2R 3.x + Spring AI 1.1 (RAG) · OpenTelemetry + VictoriaMetrics + Loki + Grafana

---

## Arquitectura — alto nivel

```
                       ┌──────────────────────────────────┐
                       │  Frontend Compose MP Web (Wasm)  │
                       │  Material 3 dark + #00E676 neon  │
                       └──────────────┬───────────────────┘
                                      │ REST + JWT (OIDC)
                                      ▼
                       ┌──────────────────────────────────┐
                       │  platform/ (MONOLITO MODULAR)    │
                       │  Spring Modulith 2.0 — 1 JVM     │
                       │                                  │
                       │  inventory · secrets             │
                       │  observability · automation      │
                       │  knowledge · identity            │
                       └─────┬────────────────────┬───────┘
                             │                    │
                  NATS JetStream events     gRPC + REST
                             │                    │
              ┌──────────────┴───────┐ ┌─────────┴───────────────┐
              │ services/            │ │ services/                │
              │ cluster-watcher      │ │ rag-ingestor + rag-query │
              │ (fabric8 informers)  │ │ (Spring AI + R2R Python) │
              └──────────────────────┘ └──────────────────────────┘
```

**Monolito modular** = un solo JAR/Pod, 6 módulos con boundaries fuertes (Spring Modulith `@ApplicationModule` + ArchUnit fitness functions).

**3 microservicios extraídos** por perfil de carga: watcher long-running, ingestor CPU-intensivo en batches, query con runtime Python (R2R).

Ver [`ARCHITECTURE.md`](./ARCHITECTURE.md) para el detalle completo + 4 ADRs.

---

## Ownership de archivos (CRÍTICO para Agent Teams)

Cuando varios agentes trabajen en paralelo, NUNCA deben editar los mismos archivos. Definición estricta:

| Agent role | Ownership (paths) |
|---|---|
| `architect` | `docs/architecture.md`, `docs/adrs/**`, `ARCHITECTURE.md`. NO escribe código. |
| `backend-dev` | `platform/{inventory,secrets,observability,automation,knowledge,identity}/**` + `services/{cluster-watcher,rag-ingestor}/**` (NO frontend, NO tests E2E) |
| `frontend-dev` | `frontend/composeApp/**` (NO backend, NO services/) |
| `qa-engineer` | `**/src/test/**`, `**/src/integrationTest/**`, `tests/e2e/**`. SOLO tests — nunca código de producción. |
| `security-reviewer` | Solo-lectura. Reporta al team-lead, no edita. |
| `devops-engineer` | `.github/workflows/**`, `k8s/**`, `Dockerfile`, `docker-compose.yml`, `routines/**` |
| `tech-writer` | `docs/**` (excepto `adrs/`), `README.md`, `CONTRIBUTING.md` (NUNCA código) |

---

## Convenciones de código

### Kotlin
- Code style: `official` (ktlint compatible)
- Formateo: ktlint + Spotless en build
- Static analysis: detekt (config en `platform/detekt.yml`)
- Inmutabilidad por defecto: `val` siempre que se pueda
- Sealed classes para domain errors, no exceptions excepto en boundaries

### Arquitectura hexagonal por módulo
```
inventory/
├── api/             # Puertos (interfaces que expone el módulo al exterior)
├── application/     # Casos de uso (orchestration)
├── domain/          # Entidades, value objects, eventos de dominio (puro Kotlin, sin Spring)
├── infrastructure/  # Adaptadores: JPA repos, K8s client, REST controllers, NATS consumers
└── package-info.java  # @ApplicationModule(displayName = "Inventory", allowedDependencies = {...})
```

Regla: `domain/` NUNCA importa de `infrastructure/`. ArchUnit lo verifica en CI.

### Tests
- Unit: JUnit 5 + Kotest matchers. Mocks con MockK.
- Integration: Testcontainers (PostgreSQL real, NATS real).
- Architecture: ArchUnit + Spring Modulith `Modulith.of(...).verify()`.
- E2E: Playwright (Wasm app) + REST contract tests.
- Coverage objetivo: ≥80% en nuevo código (JaCoCo).

### Commits
- Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`.
- Cada commit debe pasar lint + typecheck + tests (hooks pre-commit).

### Branching
- `main` = producción (protegida, sólo merges via PR).
- `develop` = integración (opcional, según necesidad).
- Features: `feat/<descripción-kebab>`.
- Fixes: `fix/<descripción-kebab>`.
- NUNCA push directo a main.

---

## Comandos esenciales

```bash
# Bootstrap dependencies
cd platform && ./gradlew dependencies

# Build all
./gradlew build

# Run platform app local
./gradlew :platform-app:bootRun

# Tests
./gradlew test                          # unit
./gradlew integrationTest               # con Testcontainers
./gradlew check                         # everything: lint + tests + ArchUnit

# Lint + format
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew detekt

# Frontend Wasm dev server
cd frontend && ./gradlew wasmJsBrowserDevelopmentRun

# Build Docker image
./gradlew :platform-app:bootBuildImage
```

---

## Segundo cerebro — reglas inquebrantables

1. **Toda documentación factual vive en `docs/` con frontmatter YAML obligatorio** (ver `docs/_template.md`).
2. **Markdown en git = ÚNICA fuente de verdad**. pgvector es índice de búsqueda, NO source-of-truth.
3. **Toda respuesta del RAG DEBE incluir citas** con formato `[source: path/file.md#section@commitsha]`.
4. **Citas inventadas son un BUG** — el citation validator las rechaza con HTTP 422.
5. **Si no hay evidencia documentada, la respuesta es literalmente "no encuentro evidencia"** — NUNCA improvisar.
6. **Tests E2E del RAG** validan queries conocidas. Deriva → build rojo.

Ver `docs/adrs/0004-second-brain-storage-and-knowledge-graph.md` para el detalle.

---

## Reglas para Agent Teams (cuando trabajen en paralelo)

- **Sizing de tareas**: 5-15 min cada una. 5-6 tareas por teammate.
- **Esperar a teammates**: si una tarea depende de otra, declarar `blockedBy`. NO empieces a implementar lo que otro hace.
- **Quality gates**: todo código debe pasar `./gradlew check` antes de marcar tarea como completa.
- **Review obligatorio**: `code-reviewer` firma antes de mergeable.
- **Security obligatorio**: `security-reviewer` firma antes de mergear features que tocan auth/secrets/red.
- **Comunicación**: usar `SendMessage` para coordinar contratos entre módulos. Broadcasts solo para anuncios.

---

## Imports relevantes (para Claude Code)

@ARCHITECTURE.md
@docs/adrs/0001-spring-modulith-vs-microservices.md
@docs/adrs/0002-compose-multiplatform-web-vs-react.md
@docs/adrs/0003-r2r-rag-stack.md
@docs/adrs/0004-second-brain-storage-and-knowledge-graph.md

---

## Antipatterns a evitar

- ❌ `try/catch (Exception e)` genérico — usar sealed result types
- ❌ Comentarios que describen QUÉ hace el código — solo PORQUÉ
- ❌ Tests con `@Disabled` sin justificación + ticket
- ❌ Mocks de PostgreSQL — usa Testcontainers
- ❌ Hardcoded secrets — todo vía Spring `@Value` desde env/Secret
- ❌ Servicios singleton con estado mutable — usar `@Component` stateless
- ❌ Edición directa de archivos generados (build/, kotlin-js-store/, etc.)
- ❌ Push directo a `main` (siempre PR)
- ❌ Cambios cross-cutting sin actualizar `docs/` correspondiente

---

## Si tienes dudas

1. Lee `ARCHITECTURE.md` primero.
2. Busca el ADR relevante en `docs/adrs/`.
3. Pregunta antes de inferir. Es **estrictamente preferible preguntar** a alucinar una decisión.
