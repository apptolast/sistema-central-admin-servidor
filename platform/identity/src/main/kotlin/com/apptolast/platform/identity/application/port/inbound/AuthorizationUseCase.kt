package com.apptolast.platform.identity.application.port.inbound

import com.apptolast.platform.identity.api.Permission
import com.apptolast.platform.identity.api.Principal
import com.apptolast.platform.identity.api.Role

/**
 * Inbound port: decisiones de autorización.
 *
 * Otros módulos (secrets, automation) llaman aquí antes de cada operación sensible.
 */
interface AuthorizationUseCase {
    fun require(principal: Principal, permission: Permission)
    fun has(principal: Principal, permission: Permission): Boolean
    fun rolesGranting(permission: Permission): Set<Role>
}

class AuthorizationDeniedException(
    val principal: Principal,
    val permission: Permission,
    val reason: String,
) : RuntimeException("$principal denied $permission: $reason")
