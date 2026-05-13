package com.apptolast.platform.identity.domain.model

import com.apptolast.platform.identity.api.Permission
import com.apptolast.platform.identity.api.Permission.INVENTORY_READ
import com.apptolast.platform.identity.api.Permission.INVENTORY_WRITE
import com.apptolast.platform.identity.api.Permission.RAG_QUERY
import com.apptolast.platform.identity.api.Permission.RUNBOOK_EXECUTE_DESTRUCTIVE
import com.apptolast.platform.identity.api.Permission.RUNBOOK_EXECUTE_INFO
import com.apptolast.platform.identity.api.Permission.RUNBOOK_EXECUTE_SAFE
import com.apptolast.platform.identity.api.Permission.RUNBOOK_VIEW
import com.apptolast.platform.identity.api.Permission.SECRET_LIST
import com.apptolast.platform.identity.api.Permission.SECRET_ROTATE
import com.apptolast.platform.identity.api.Permission.SLO_ACK_ALERT
import com.apptolast.platform.identity.api.Permission.SLO_DEFINE
import com.apptolast.platform.identity.api.Role

/**
 * Matriz Role → Permission. Single source of truth de autorización interna.
 *
 * Cambios aquí deben venir con un test en `AuthorizationMatrixTest` que valide
 * el caso. Reason: evitar drift entre lo que decimos en los runbooks y lo que
 * realmente enforced.
 *
 * Si necesitas permisos por-recurso (ej. solo runbooks de cluster-ops), añade
 * un `ResourceScope` en una iteración posterior — Phase 4 mantiene grain por Role.
 */
object AuthorizationMatrix {

    private val matrix: Map<Role, Set<Permission>> = mapOf(
        Role.ADMIN to setOf(
            INVENTORY_READ, INVENTORY_WRITE,
            RUNBOOK_VIEW, RUNBOOK_EXECUTE_INFO, RUNBOOK_EXECUTE_SAFE, RUNBOOK_EXECUTE_DESTRUCTIVE,
            SECRET_LIST, SECRET_ROTATE,
            SLO_DEFINE, SLO_ACK_ALERT,
            RAG_QUERY,
        ),
        Role.ONCALL to setOf(
            INVENTORY_READ,
            RUNBOOK_VIEW, RUNBOOK_EXECUTE_INFO, RUNBOOK_EXECUTE_SAFE,
            SECRET_LIST,
            SLO_ACK_ALERT,
            RAG_QUERY,
        ),
        Role.VIEWER to setOf(
            INVENTORY_READ,
            RUNBOOK_VIEW,
            RAG_QUERY,
        ),
        Role.AGENT to setOf(
            INVENTORY_READ,
            RUNBOOK_VIEW,
            RAG_QUERY,
        ),
    )

    fun grants(role: Role): Set<Permission> = matrix[role] ?: emptySet()

    fun rolesGranting(permission: Permission): Set<Role> =
        matrix.entries.filter { permission in it.value }.map { it.key }.toSet()

    fun allows(roles: Set<Role>, permission: Permission): Boolean =
        roles.any { permission in grants(it) }
}
