# ARCHITECTURE.md — Arquitectura, decisiones, roadmap

> Documento maestro de arquitectura. Cuando esto y un ADR difieran, prevalece el ADR (más específico).
>
> Última revisión: 2026-05-13.

---

## 1. Visión

Internal Developer Platform (IDP) single-tenant para AppToLast. Capa de control + segundo cerebro sobre un cluster Kubernetes single-node en un VPS Hetzner CPX62 (16 vCPU, 32 GB RAM). Reemplaza progresivamente a Rancher UI, scripts manuales, y "tener que entrar a 3 sitios" para conocer el estado del cluster.

## 2. Decisiones arquitectónicas clave

### 2.1 — Modular monolith con extracción selectiva

**Spring Modulith 2.0 GA** para el núcleo, con 3 microservicios extraídos por perfil de carga. Ver [`docs/adrs/0001-spring-modulith-vs-microservices.md`](./docs/adrs/0001-spring-modulith-vs-microservices.md).

Razón principal: 42% de organizaciones que adoptaron microservicios consolidan a monolitos modulares en 2025-2026 (CNCF Q1 2026, casos Shopify, Amazon Prime Video). Para 6 bounded contexts en single-node con 8 GB libres, modular monolith añade menos fricción y conserva las boundaries que importan.

### 2.2 — Stack técnico

| Capa | Tecnología | Versión |
|------|-----------|---------|
| Lenguaje + JVM | Kotlin 2.3.21 + Eclipse Temurin JDK 21 LTS | virtual threads habilitados |
| Backend framework | Spring Boot 3.5.x + Spring Modulith 2.0 GA | |
| Build | Gradle 8.10+ Kotlin DSL | multi-módulo |
| Cliente Kubernetes | fabric8-kubernetes-client 7.x | informers + CRDs |
| Persistencia | Spring Data JPA + Hibernate + Flyway 10.x | una BD lógica por módulo (schemas) |
| BD | PostgreSQL 16 + pgvector 0.8 | instancia única compartida |
| Mensajería async | NATS JetStream 2.10+ | eventos cluster + dominio |
| RPC interno (microservicios) | gRPC-Kotlin 1.4+ | |
| Frontend | Compose Multiplatform Web 1.10.2 (target wasmJs) + Material 3 + Koin 4.2 + Ktor 3.4 | hereda patrones de GreenhouseAdmin |
| Auth | Keycloak 26.6 (OIDC) | self-hosted en cluster |
| Gateway | Traefik 3.3.6 | YA desplegado, no añadimos Spring Cloud Gateway |
| RAG | R2R (SciPhi-AI) 3.x + Spring AI 1.1 + Cognee (Fase 7 opcional) | citation-first |
| Observabilidad | OpenTelemetry Collector + VictoriaMetrics + Loki + Grafana | ~2 GB RAM total |
| Tests | JUnit 5 + Kotest + Testcontainers + ArchUnit + Playwright | |
| Calidad | ktlint + detekt + Spotless | obligatorios en CI |
| CI/CD | GitHub Actions + Keel (auto-update imágenes) | |

### 2.3 — Single-tenant, single-node

- Sin diseño multi-tenant prematuro.
- Sin HA real (1 nodo): downtime de reboots aceptado; servicios diseñados para arranque en frío rápido.
- Backup off-site de Longhorn → Hetzner Storage Box (primer entregable de seguridad en Fase 6).

### 2.4 — Segundo cerebro: documentación-como-código + RAG con citas obligatorias

**Principios anti-alucinación (defensa en profundidad de 5 capas)**:

1. Fuente de verdad = Markdown en git con frontmatter YAML.
2. LLM nunca tiene los datos memorizados — los recupera bajo demanda de chunks reales.
3. Toda respuesta DEBE incluir citas con formato `[source: path/file.md#section@commitsha]`.
4. Citation validator (middleware Kotlin) rechaza respuestas con citas no resolvibles (HTTP 422).
5. Score threshold: si top-K chunks < 0.6 similitud → "no encuentro evidencia documentada".

Ver [`docs/adrs/0003-r2r-rag-stack.md`](./docs/adrs/0003-r2r-rag-stack.md) y [`docs/adrs/0004-second-brain-storage-and-knowledge-graph.md`](./docs/adrs/0004-second-brain-storage-and-knowledge-graph.md).

### 2.5 — Conscient inheritance: lo que tomamos de Ubicloud

**No** instalamos [Ubicloud](https://github.com/ubicloud/ubicloud) (multi-tenant, ~4-6 GB RAM, problema distinto). **Sí** heredamos su filosofía:

- Pattern **control-plane + data-plane**: el monolito modular ES el control plane; K8s + apps son el data plane.
- Open-source by default + IaC-friendly.
- ABAC (atributos + roles + permisos) como evolución natural del RBAC inicial.

---

## 3. Bounded contexts (6 módulos + 3 servicios extraídos)

### Módulos del monolito (Spring Modulith)

| # | Módulo | Responsabilidad |
|---|--------|-----------------|
| 1 | `inventory` | Catálogo vivo: pods, services, ingresses, certs, PVCs, DNS Cloudflare, nodes Hetzner. Topología. |
| 2 | `secrets` | Inventario de secrets (NO contenido — sólo qué existe, dónde, quién owner). Integra Passbolt. |
| 3 | `observability` | Agregador: salud (Kuma), logs (Dozzle), métricas (VictoriaMetrics), eventos K8s, salida de los 18 cronjobs cluster-ops. |
| 4 | `automation` | Orchestrator de cronjobs/scripts: K8s CronJobs + n8n workflows + host crontab. Trigger manual + histórico. |
| 5 | `knowledge` | Frontend del segundo cerebro: editor markdown, queries RAG, citas. Write-through a git. |
| 6 | `identity` | RBAC del IDP. OIDC con Keycloak. Roles `admin`, `viewer`, `operator`. |

### Microservicios extraídos

| Servicio | Por qué extraído |
|----------|------------------|
| `cluster-watcher` | Long-running watch del K8s API (fabric8 informers). Heap separada para no penalizar el monolito; reinicio aislado. |
| `rag-ingestor` | CPU-intensivo (embeddings). Batches programados. No debe interferir con el monolito. |
| `rag-query-service` | Wrapper Python sobre R2R (R2R es Python nativo) + facade Spring Boot Kotlin. |

---

## 4. Roadmap por fases — 8 fases, ~22 semanas

| Fase | Semanas | Entregable mínimo |
|------|---------|-------------------|
| **0 — Bootstrap** | 1 | Repo + CI verde + Spring Boot mínimo levantando con `/health` 200 |
| **1 — Inventory + cluster-watcher + UI esqueleto** | 2-4 | UI muestra los 126 pods + 80 DNS + 30 PVCs en tiempo real |
| **2 — Observability + OTEL/VictoriaMetrics/Loki/Grafana** | 5-7 | Alerta Kuma cae → UI → Discord → histórico |
| **3 — Segundo cerebro: knowledge + rag-ingestor + rag-query** | 8-12 | Preguntas factuales responden con citas resolvibles; sin evidencia → "no encuentro evidencia" |
| **4 — Secrets + Passbolt + Identity + Keycloak** | 13-16 | Login OIDC, RBAC, password n8n migrado a Secret real |
| **5 — Automation + n8n + cluster-ops cronjobs unificados** | 17-19 | Una pantalla con los 30 cronjobs distribuidos + trigger manual |
| **6 — Hardening: Longhorn off-site, pg_dump, network policies** | 20-22 | Backup off-site funcionando, dolores P0/P1 del dossier resueltos |
| **7 — Topology graph + agentic queries + Cognee + design system** | 23-26 | Grafo de dependencias multi-hop con citas; Open WebUI integrado |

Cada fase termina con un PR mergeable, tests verdes, documentación actualizada, y al menos un ADR escrito si la fase introduce decisiones nuevas.

---

## 5. Patrones técnicos transversales

### Estructura hexagonal por módulo

```
<module>/
├── api/              # Puertos (interfaces públicas del módulo)
│   ├── events/       # Eventos de dominio publicados
│   └── commands/     # Comandos que el módulo acepta
├── application/      # Casos de uso
├── domain/           # Entidades, value objects, lógica pura
└── infrastructure/   # Adaptadores: REST, JPA, K8s, NATS
```

ArchUnit verifica:
- `domain/` no importa de `infrastructure/`
- `domain/` no importa de `org.springframework.*` excepto annotations específicas
- Inter-module access sólo vía `api/`

### Comunicación inter-módulo

- **Síncrona**: vía interfaces en `api/` (event listeners + caller). Spring Modulith verifica que sólo se acceda a `api/`.
- **Asíncrona**: eventos de dominio `@DomainEvent` + Spring Modulith event publication registry → NATS JetStream para externalización.

### Comunicación monolito ↔ microservicios extraídos

- **cluster-watcher → monolito**: NATS JetStream (`cluster.events.*`).
- **monolito → rag-ingestor**: NATS JetStream (`docs.changed`).
- **monolito → rag-query**: gRPC sync request/response.

### Citación obligatoria en RAG (anti-alucinación)

Formato de cita:
- **Estática**: `[source: docs/services/postgres-n8n.md#deploy@a1b2c3d]`
- **Live snapshot**: `[source: live:inventory@2026-05-13T18:00Z]`

El `CitationValidator` (middleware Kotlin) parsea cada respuesta, valida que cada cita resuelve a un `chunk_id` o `live_doc_id` real en pgvector. Citas inventadas → HTTP 422.

### Knowledge graph en 3 capas

1. **Declarativa** (frontmatter YAML, `declared=true, confidence=1.0`) — Fase 3.
2. **Extraída** (R2R parsea texto, `declared=false, confidence=0.x`) — Fase 3.
3. **Inferida multi-hop** (Cognee, DFS por `depends-on`) — Fase 7.

Ver [`docs/adrs/0004-second-brain-storage-and-knowledge-graph.md`](./docs/adrs/0004-second-brain-storage-and-knowledge-graph.md).

---

## 6. Restricciones operacionales (single-node, 8 GB libres)

| Componente | RAM presupuestada |
|-----------|-------------------|
| Monolito `platform-app` | 1-2 GB |
| `cluster-watcher` | 256 MB |
| `rag-ingestor` (batch only) | 512 MB (pico) |
| `rag-query-service` (R2R + facade) | 512 MB + 200 MB |
| Keycloak + Postgres | 1.2-1.5 GB |
| NATS JetStream | 200-400 MB |
| VictoriaMetrics + Loki + Grafana | 1.5-2 GB |
| OpenTelemetry Collector | 100-200 MB |
| **Total IDP** | **~6 GB** |

Resto del cluster (126 pods existentes): 22 GB. Holgura ~4 GB.

Si se cruza el 90% sostenido, **se congelan features nuevas** hasta optimizar.

---

## 7. Cosas que NO haremos (anti-scope-creep)

- ❌ Multi-tenancy (single-tenant, AppToLast solo).
- ❌ Mobile apps (Compose MP soporta Android/iOS pero NO los compilamos en Fase 0-7).
- ❌ Instalar Ubicloud (problema distinto: cloud multi-tenant sobre bare metal).
- ❌ Backstage o Port (construimos a medida).
- ❌ Service mesh (Istio, Linkerd, Cilium) — single-node, no aporta.
- ❌ ArgoCD / Flux — mantenemos Fleet+Rancher+Keel actuales.
- ❌ Prometheus operator — VictoriaMetrics es 10× más ligero.
- ❌ Kafka — NATS es suficiente y consume 3× menos RAM.
- ❌ Reemplazar n8n, Passbolt, OpenClaw, Langflow — los integramos.
- ❌ Reescribir los 18 cronjobs de `cluster-ops` — los **mostramos** y **disparamos** desde el IDP.

---

## 8. Architecture Decision Records (ADRs)

Los ADRs son **inmutables una vez merged**. Si una decisión cambia, se crea un ADR nuevo que la **supersede**.

| # | Título | Status |
|---|--------|--------|
| 0001 | Spring Modulith vs microservices distribuidos | Accepted |
| 0002 | Compose Multiplatform Web vs React/Vue | Accepted |
| 0003 | R2R + Spring AI para RAG | Accepted |
| 0004 | Almacenamiento y knowledge graph del segundo cerebro | Accepted |

Plantilla para nuevos ADRs en [`docs/adrs/_template.md`](./docs/adrs/_template.md) (será creada en su momento).

---

## 9. Cómo este sistema te enorgullecerá

(Por qué es defendible en una entrevista senior)

1. **Decisión arquitectónica con evidencia**: modular monolith justificado por datos 2026 + casos reales, no por dogma.
2. **Hexagonal estricto con ArchUnit**: el compilador audita las boundaries.
3. **Full Kotlin sello**: backend + frontend en el mismo lenguaje, sin context switching.
4. **RAG anti-alucinación by design**: capas explícitas, citation validator, tests E2E.
5. **Automatización meta**: el propio proyecto se autoconstruye via Claude Code Routines + Agent Teams.
6. **Integración sobre reemplazo**: convivimos con n8n, Passbolt, Keycloak, OpenClaw. Criterio senior.
7. **Observabilidad propia con ~2 GB de RAM**: eficiencia probada.
8. **Documentación-como-código**: ADRs inmutables, frontmatter obligatorio, write-through git.
9. **Defensa explícita de límites**: §7 dice claramente qué NO haremos. Madurez de scope.
