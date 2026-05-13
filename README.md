# Sistema Central de Administración del Servidor

> **AppToLast IDP** — Internal Developer Platform, full Kotlin, distribuida, anti-alucinación.
>
> Capa de control + segundo cerebro sobre toda la infraestructura de AppToLast.

---

## Qué es

Una **Internal Developer Platform** propia, autohosteable en nuestro propio cluster Kubernetes, que unifica:

- **Inventario vivo** de toda la infraestructura (126+ pods, 80 DNS, 30 PVCs, 9 PostgreSQL, certs, secretos…)
- **Observabilidad agregada** (alertas Discord/Telegram, Kuma uptime, Dozzle logs, métricas K8s, salidas de los 18 cronjobs)
- **Automatización** (orquestador de cronjobs + workflows n8n + scripts del host)
- **Segundo cerebro RAG** sobre la infraestructura documentada — con citas obligatorias y cero alucinación por diseño
- **Identidad / RBAC** vía Keycloak + integración con Passbolt

Construida sobre **Spring Modulith 2.0 + Kotlin 2.3 + Compose Multiplatform Web (Wasm)**, hosteada en un VPS Hetzner CPX62 (16 vCPU, 32 GB RAM) compartido con el resto del cluster.

## Por qué existe

Hoy el conocimiento crítico de la infra está disperso entre Rancher, panel Hetzner, Cloudflare, Passbolt, terminales SSH, Dozzle, Kuma, n8n, Discord y la cabeza de los desarrolladores. Este sistema centraliza todo eso en una única plataforma operada por sus dueños — sin pagar a un proveedor cloud por features que ya existen en su propia infraestructura.

## Estado

🚧 **Fase 0 — Bootstrap** (mayo 2026). El roadmap completo (8 fases, ~22 semanas) vive en [`ARCHITECTURE.md`](./ARCHITECTURE.md) y los ADRs.

## Stack

| Capa | Tecnología | Versión |
|------|-----------|---------|
| Lenguaje | Kotlin | 2.3.21 |
| JVM | Eclipse Temurin JDK | 21 LTS |
| Framework | Spring Boot | 3.5.x |
| Modularidad | Spring Modulith | 2.0 GA |
| Cliente K8s | fabric8-kubernetes-client | 7.x |
| BD relacional | PostgreSQL + pgvector | 16 / 0.8 |
| Frontend | Compose Multiplatform Web (Wasm) | 1.10.2 |
| Auth | Keycloak | 26.6 |
| Mensajería | NATS JetStream | 2.10+ |
| RAG | R2R (SciPhi-AI) + Spring AI | 3.x / 1.1 |
| Observabilidad | OpenTelemetry + VictoriaMetrics + Loki + Grafana | últimas |

Ver [`docs/adrs/`](./docs/adrs/) para las decisiones técnicas con justificación.

## Cómo arrancar (desarrollo local)

> Requiere JDK 21 y Docker. Pendiente de la Fase 0 — actualmente sólo compilan los stubs.

```bash
git clone git@github.com:apptolast/sistema-central-admin-servidor.git
cd sistema-central-admin-servidor/platform
./gradlew :platform-app:build
./gradlew :platform-app:bootRun
# health check
curl http://localhost:8080/actuator/health
```

## Cómo está organizado

```
.
├── platform/         Monolito modular Spring Modulith (6 bounded contexts)
├── services/         Microservicios extraídos (cluster-watcher, rag-ingestor, rag-query)
├── frontend/         Compose Multiplatform Web (Wasm)
├── docs/             Segundo cerebro: Markdown + frontmatter YAML — fuente de verdad
│   ├── adrs/         Architecture Decision Records
│   ├── runbooks/     27+ procedimientos operacionales
│   ├── services/     Documentación por servicio del cluster
│   └── infrastructure/  Hetzner, K8s, networking, storage, identidad
├── k8s/              Helm charts y manifiestos de despliegue
├── routines/         Configs de Claude Code Routines (automation)
├── .claude/          Subagentes, comandos, hooks (Agent Teams)
└── .github/workflows/  CI/CD
```

## Documentación del proyecto

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — Decisiones arquitectónicas + roadmap por fases
- [`CLAUDE.md`](./CLAUDE.md) — Contexto maestro para sesiones de Claude Code
- [`docs/adrs/0001-spring-modulith-vs-microservices.md`](./docs/adrs/0001-spring-modulith-vs-microservices.md)
- [`docs/adrs/0002-compose-multiplatform-web-vs-react.md`](./docs/adrs/0002-compose-multiplatform-web-vs-react.md)
- [`docs/adrs/0003-r2r-rag-stack.md`](./docs/adrs/0003-r2r-rag-stack.md)
- [`docs/adrs/0004-second-brain-storage-and-knowledge-graph.md`](./docs/adrs/0004-second-brain-storage-and-knowledge-graph.md)

## Inspiración (filosófica, no técnica)

[Ubicloud](https://github.com/ubicloud/ubicloud) — alternativa open-source a los hyperscalers. Nos resuena su patrón **control-plane + data-plane**, su filosofía open-source-first, y su ABAC. NO instalamos Ubicloud (es multi-tenant, irrelevante para single-tenant). Tomamos prestada la idea de "devuélveme el control de mi infraestructura".

## Licencia

Privado — uso interno AppToLast.
