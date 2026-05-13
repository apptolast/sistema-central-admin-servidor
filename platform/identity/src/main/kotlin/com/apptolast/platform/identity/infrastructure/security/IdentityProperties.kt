package com.apptolast.platform.identity.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuración OIDC del IDP. Properties prefix: `identity.oidc.*`.
 *
 * En profile `dev` (default fuera de cluster), `enabled=false` → SecurityConfig
 * desactiva la cadena OAuth2 y permitAll todo, lo que permite arrancar local
 * sin Keycloak.
 *
 * En profile `prod` (o cuando el operador lo activa explícitamente),
 * `enabled=true` y se requiere `issuerUri` apuntando a Keycloak.
 */
@ConfigurationProperties(prefix = "identity.oidc")
data class IdentityProperties(
    val enabled: Boolean = false,
    val issuerUri: String = "",
    /**
     * Claim del JWT que contiene los realm roles. Keycloak default:
     * `realm_access.roles` (JSON path). Override si Keycloak está customizado.
     */
    val realmRolesClaim: String = "realm_access.roles",
    /**
     * Audience esperado en el JWT. Si no-blank, se valida el `aud` claim.
     */
    val audience: String = "idp-backend",
)
