package com.apptolast.platform.identity.api

/**
 * API pública del módulo identity. Otros módulos importan SOLO esto.
 *
 * El `Principal` es el actor autenticado en un request. Se construye a partir
 * del JWT de Keycloak (sub, email, groups, roles).
 */
data class Principal(
    val userSub: String,
    val email: String,
    val displayName: String,
    val roles: Set<Role>,
    val groups: Set<String> = emptySet(),
) {
    init {
        require(userSub.isNotBlank()) { "userSub must not be blank" }
    }

    fun hasRole(role: Role): Boolean = role in roles
    fun isAdmin(): Boolean = Role.ADMIN in roles
    fun isOncall(): Boolean = Role.ONCALL in roles
}

/**
 * Roles internos. Mapean a Keycloak realm roles via JWT claim `realm_access.roles`.
 *
 * Mantener corto y semántico. Para permisos finos, usar `Permission`.
 */
enum class Role {
    /** Acceso total. Puede ejecutar runbooks destructivos, ver secrets, modificar SLOs. */
    ADMIN,

    /** Acceso de operación. Puede ejecutar runbooks SAFE_AUTO y acknowledge alerts. */
    ONCALL,

    /** Solo lectura del inventario, runbooks, RAG. */
    VIEWER,

    /** Solo el agente RAG. Puede leer documentos pero no ejecutar acciones. */
    AGENT,
}

/**
 * Permisos finos. Usar cuando un rol no basta para distinguir capabilities.
 */
enum class Permission {
    INVENTORY_READ,
    INVENTORY_WRITE,

    RUNBOOK_VIEW,
    RUNBOOK_EXECUTE_INFO,
    RUNBOOK_EXECUTE_SAFE,
    RUNBOOK_EXECUTE_DESTRUCTIVE,

    SECRET_LIST,
    SECRET_ROTATE,

    SLO_DEFINE,
    SLO_ACK_ALERT,

    RAG_QUERY,
}
