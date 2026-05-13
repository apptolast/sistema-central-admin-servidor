/**
 * Secrets bounded context (Phase 4).
 *
 * <p>Frontend para Passbolt. NO almacenamos secrets en este módulo — sólo
 * metadata visible (nombre, owner, last-rotation) y audit log de accesos.
 * El secret real vive en Passbolt y se cifra E2E con GPG por usuario.
 *
 * <p>Integración:
 * - {@code infrastructure.passbolt.PassboltApiClient} consume la API REST
 * - Audit log local en {@code secret_access_log} tabla
 * - El usuario autentica vía OIDC (Keycloak) y opera con su propia llave GPG
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Secrets",
    allowedDependencies = {"identity"}
)
package com.apptolast.platform.secrets;
