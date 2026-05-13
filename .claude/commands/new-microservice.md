---
description: Scaffolds a new extracted microservice in services/<name>/ (use only when the load profile justifies extraction over a monolith module)
allowed-tools: Write, Edit, Bash, Read
argument-hint: <service-name>
---

Crea un nuevo microservicio extraído en `services/<service-name>/`. Reservar SOLO para servicios cuya carga justifica una JVM separada (long-running, CPU-intensivo batch, runtime distinto al Kotlin).

ANTES de crear: verificar que la decisión está respaldada por un ADR. Si no existe, crear uno primero (`docs/adrs/000N-extract-<service>-service.md`) que justifique la extracción frente a "ser un módulo más en el monolito".

Pasos:

1. Validar `$ARGUMENTS` (kebab-case, no colisiona con módulos existentes ni con `cluster-watcher`/`rag-ingestor`/`rag-query` ya planeados).
2. Pedir al usuario confirmar el ADR de justificación.
3. Crear estructura:
   ```
   services/<name>/
   ├── README.md                   (qué hace + por qué es servicio separado + link al ADR)
   ├── Dockerfile                  (multi-stage: builder gradle:8.10-jdk21 → runtime distroless o jre-alpine)
   ├── build.gradle.kts            (Spring Boot starter mínimo + libs específicas)
   ├── src/main/kotlin/com/apptolast/services/<name>/
   │   ├── <Name>Application.kt    (@SpringBootApplication)
   │   ├── api/                    (REST/gRPC controllers expuestos)
   │   ├── application/            (lógica)
   │   ├── domain/                 (modelo)
   │   └── infrastructure/         (adapters)
   ├── src/main/resources/
   │   ├── application.yml
   │   └── logback-spring.xml
   ├── src/test/kotlin/com/apptolast/services/<name>/
   │   └── <Name>ApplicationTest.kt   (smoke test que arranca contexto)
   └── helm/<name>/                (chart Helm con Deployment, Service, IngressRoute si aplica, NetworkPolicy)
       ├── Chart.yaml
       ├── values.yaml
       └── templates/
   ```
4. Actualizar `platform/settings.gradle.kts` o crear `services/settings.gradle.kts` con `include`.
5. Crear el chart Helm con NetworkPolicy restrictiva (default deny + allow específicos).
6. Configurar resource limits sensatos en values.yaml (default 256Mi/100m → 1Gi/1000m).
7. Configurar liveness + readiness probes via Spring Boot Actuator.
8. Marcar imagen con `keel.sh/policy=force` y `keel.sh/trigger=poll` en deployment.yaml.
9. Actualizar `ARCHITECTURE.md` §3 (microservicios extraídos) con el nuevo servicio.
10. Ejecutar `./gradlew :services:<name>:build && helm lint services/<name>/helm/<name>/`.
11. Reportar al usuario.

Reglas:
- NUNCA extraer un microservicio sin ADR justificándolo.
- Cada servicio debe poder arrancar standalone con `./gradlew :services:<name>:bootRun`.
- Comunicación entre servicios SOLO vía NATS JetStream (eventos) o gRPC (RPC sincrónico bien justificado).
- NO endpoints REST públicos sin pasar por Traefik (sin NodePorts ni LoadBalancer extras).
