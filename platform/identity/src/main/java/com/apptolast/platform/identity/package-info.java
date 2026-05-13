/**
 * Identity bounded context (Phase 4 first half).
 *
 * <p>Frontend para Keycloak OIDC. Resuelve el `Principal` autenticado en
 * cada request y aplica RBAC interno via `AuthorizationPolicy`.
 *
 * <p><b>Allowed deps:</b> ninguna. Otros módulos importan
 * {@code identity.api.Principal} para identificar al actor.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {}
)
package com.apptolast.platform.identity;
