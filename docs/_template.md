---
# ============================================================================
# PLANTILLA OBLIGATORIA para todo documento en docs/
# Copia este archivo, renómbralo, rellena el frontmatter y elimina los comentarios.
# El rag-ingestor parsea estos campos para construir el knowledge graph.
# ============================================================================

# Título humano del documento. Debe ser único en el repo.
title: "Nombre del recurso o procedimiento"

# Tipo del documento. Uno de:
#   service | runbook | infrastructure | adr | host | network | policy | architecture
type: service

# Owner principal — quién mantiene este doc. GitHub username.
owner: pablo

# Comando, URL o referencia que es la fuente AUTORITATIVA de los datos.
# El rag-ingestor podrá ejecutarlo (o el cluster-watcher lo replicará) para
# verificar que el contenido del doc sigue alineado con la realidad.
source-of-truth: "kubectl -n <namespace> get <resource> <name>"

# Fecha (ISO 8601) en que se verificó por última vez que el contenido refleja la realidad.
# La rutina docs-drift-detector alertará cuando este valor sea > 30 días.
last-verified: 2026-05-13

# Etiquetas para filtrado en la UI y búsqueda facetada.
tags:
  - database

# Status del documento: stable | beta | deprecated | superseded
status: stable

# Si este doc está superado por otro, indica la ruta relativa al nuevo.
superseded-by: null

# ────────── RELACIONES DEL KNOWLEDGE GRAPH (capa 1, declarativa) ──────────
# Formato: tipo:identificador
# Tipos válidos: service, namespace, pvc, configmap, secret, ingress, dns,
#                cronjob, runbook, repo, person, dashboard, alert

# Recursos de los que depende este servicio/concepto para funcionar.
depends-on: []
  # - namespace:n8n
  # - pvc:postgres-n8n-longhorn

# Recursos que usan/dependen de este servicio/concepto.
used-by: []
  # - service:n8n-prod

# Runbooks relacionados (si algo falla, se ejecutan estos).
related-runbooks: []
  # - RB-10-pg-connections-high

# Dashboards relevantes para monitorizar este recurso.
related-dashboards: []
  # - grafana:postgres-overview

# Alertas configuradas sobre este recurso.
related-alerts: []
  # - alert:postgres-connection-pool-exhausted

# Otros documentos relacionados (no necesariamente dependencia, sólo relevantes).
see-also: []
  # - infrastructure/networking-traefik.md
---

# Nombre del recurso o procedimiento

## Resumen

Una o dos frases que respondan: ¿qué es esto?

## Contexto / Por qué existe

Por qué este recurso o procedimiento es necesario. La motivación.

## Estado actual (verificado el {{last-verified}})

Datos factuales. **Usa la fuente de verdad (`source-of-truth` arriba) para cada afirmación.** Si no puedes verificarlo, no lo escribas.

Ejemplo:
- Versión: 16.10 (verificado vía `kubectl -n n8n exec postgres-n8n-0 -- psql --version`)
- Réplicas: 1
- Capacidad: 10 Gi (Longhorn, single-replica)
- Última verificación: 2026-05-13

## Operación normal

Cómo se opera este recurso en condiciones normales.

## Procedimientos comunes

### Procedimiento 1

Paso a paso.

### Procedimiento 2

Paso a paso.

## Cuándo escalar / pedir ayuda

Cuándo este recurso NO está en condiciones normales y hace falta intervención manual.

## Histórico relevante

- 2026-05-13 — Documento creado.
- (futuro) — Cambios mayores se anotan aquí, además de en git history.

## Referencias

- Enlaces a docs externos, RFCs, blog posts, etc.
