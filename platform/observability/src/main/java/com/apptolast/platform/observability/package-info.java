/**
 * Observability bounded context (Phase 2).
 *
 * <p>Receives OTLP telemetry from apps, aggregates SLOs, triggers alerts.
 * Storage: VictoriaMetrics (metrics) + VictoriaLogs (logs). See ADR-0006.
 *
 * <p><b>Allowed dependencies:</b> may consume events from
 * {@code inventory} (e.g. when a pod restarts, attach trace context).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Observability",
    allowedDependencies = {"inventory"}
)
package com.apptolast.platform.observability;
