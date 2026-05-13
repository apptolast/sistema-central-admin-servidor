package com.apptolast.platform.identity.infrastructure.security

import com.apptolast.platform.identity.api.Principal
import com.apptolast.platform.identity.api.Role
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Convierte un JWT validado por Spring Security (issuer Keycloak) a
 * [Principal] de dominio.
 *
 * Mapea:
 *  - `sub` → userSub
 *  - `email` → email (fallback `sub`)
 *  - `preferred_username` o `name` → displayName
 *  - `realm_access.roles` (JSON path) → Set<Role> aplicando case-insensitive
 *    matching con el enum. Roles desconocidos se descartan (NO error — el
 *    realm puede tener roles para otras apps).
 *  - `groups` claim si existe.
 *
 * Wave-D D2 anti-hallucination: si el JWT no trae claim de roles, devuelve
 * `roles = emptySet()` (Principal sin permisos) en vez de inventar VIEWER.
 * La cadena Spring Security ya verificó la firma, pero la AUTORIZACIÓN es
 * explícita.
 */
object JwtPrincipalMapper {

    private val log = LoggerFactory.getLogger(JwtPrincipalMapper::class.java)

    fun fromJwt(jwt: Jwt, realmRolesClaim: String = "realm_access.roles"): Principal {
        val sub = jwt.subject
        require(!sub.isNullOrBlank()) { "JWT subject is required" }
        return Principal(
            userSub = sub,
            email = jwt.getClaimAsString("email") ?: sub,
            displayName = jwt.getClaimAsString("preferred_username")
                ?: jwt.getClaimAsString("name")
                ?: sub,
            roles = extractRoles(jwt, realmRolesClaim),
            groups = extractGroups(jwt),
        )
    }

    private fun extractRoles(jwt: Jwt, claimPath: String): Set<Role> {
        val parts = claimPath.split(".")
        val firstClaim = jwt.getClaim<Any?>(parts.first()) ?: return emptySet()
        val rawRoles: List<*> = when (parts.size) {
            1 -> firstClaim as? List<*> ?: return emptySet()
            2 -> {
                val nested = firstClaim as? Map<*, *> ?: return emptySet()
                nested[parts[1]] as? List<*> ?: return emptySet()
            }
            else -> {
                log.warn("realmRolesClaim path '{}' tiene >2 niveles; no soportado", claimPath)
                return emptySet()
            }
        }
        return rawRoles
            .mapNotNull { it?.toString()?.trim()?.uppercase() }
            .mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() }
            .toSet()
    }

    private fun extractGroups(jwt: Jwt): Set<String> {
        val raw = jwt.getClaim<Any?>("groups") as? List<*> ?: return emptySet()
        return raw.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}
