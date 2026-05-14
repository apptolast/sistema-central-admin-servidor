---
title: "Codex autonomous quality loop"
owner: pablo
type: operations
status: active
last-verified: 2026-05-14
tags: [codex, quality-gate, autonomous-loop, stabilization]
---

# Codex autonomous quality loop

## Objetivo

Convertir "0 errores, 0 fallos, 0 TODO accionable" en un estado verificable del repo.
La unidad de verdad no es una promesa del agente: es el resultado de `scripts/quality-gate.sh`.

## Definition of Done

Una tarea queda cerrada solo si:

1. El cambio está implementado en código, tests o documentación versionada.
2. No queda `@Disabled`, `TODO(...)`, `TODO:`, `FIXME`, `XXX`, `HACK`, `NotImplemented` ni `not yet implemented` en rutas técnicas.
3. `platform ./gradlew check` pasa.
4. `services/cluster-watcher`, `services/rag-ingestor` y `services/rag-query` pasan `build`.
5. `frontend` genera `wasmJsBrowserDistribution`.
6. Todos los Helm charts hacen `helm lint` y `helm template`.
7. Los XML de JUnit no contienen tests saltados.

## Loop operativo

1. Escanear estado real con `scripts/quality-gate.sh`.
2. Clasificar cada fallo:
   - P0: build roto, test rojo, despliegue roto, seguridad/auth/secrets.
   - P1: comportamiento incompleto ya expuesto por UI/API.
   - P2: deuda técnica que no rompe gates pero aumenta riesgo.
3. Corregir una causa raíz por iteración.
4. Añadir o reactivar cobertura antes de cerrar.
5. Reejecutar el gate completo.
6. Si algo requiere infraestructura externa, documentar el bloqueo con comando exacto, evidencia y siguiente acción.

## Límites

No se inventa estado del cluster ni credenciales. Si el gate local pasa pero el runtime desplegado falla, la siguiente iteración debe inspeccionar `kubectl`, `helm`, logs y health checks reales antes de tocar código.
