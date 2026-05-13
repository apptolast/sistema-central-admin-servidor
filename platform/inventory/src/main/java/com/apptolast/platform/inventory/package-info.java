/**
 * Inventory bounded context.
 *
 * <p>Catálogo declarativo de recursos del cluster Kubernetes: Pods, Services,
 * Ingresses, PersistentVolumeClaims, Certificates, DNS records, Volumes.
 *
 * <p>El módulo es alimentado por el servicio externo {@code cluster-watcher},
 * que publica eventos de cambio. Bus de eventos:
 * <ul>
 *   <li>Fase 1: in-memory (ver ADR-0005, justificación: 1 nodo, baja carga).</li>
 *   <li>Fase 2: NATS JetStream (escalabilidad + durability).</li>
 * </ul>
 *
 * <p><b>API pública exportada</b> ({@code inventory.api.**}):
 * Eventos {@code PodObserved}, {@code ServiceObserved}, {@code IngressObserved},
 * {@code PvcObserved}, {@code CertObserved} consumibles por otros módulos.
 *
 * <p><b>Internals</b> ({@code domain.**}, {@code application.**},
 * {@code infrastructure.**}): no importables desde fuera del módulo.
 * Enforced por ArchUnit + Spring Modulith.verify().
 *
 * @see com.apptolast.platform.inventory.api.events.PodObserved
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Inventory",
    allowedDependencies = {}
)
package com.apptolast.platform.inventory;
