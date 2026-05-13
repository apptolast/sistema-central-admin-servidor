package com.apptolast.platform.identity.application.service

import com.apptolast.platform.identity.api.Permission
import com.apptolast.platform.identity.api.Principal
import com.apptolast.platform.identity.api.Role
import com.apptolast.platform.identity.application.port.inbound.AuthorizationDeniedException
import com.apptolast.platform.identity.application.port.inbound.AuthorizationUseCase
import com.apptolast.platform.identity.domain.model.AuthorizationMatrix
import org.springframework.stereotype.Service

/**
 * Implementación del [AuthorizationUseCase]. Single source of truth: el
 * [AuthorizationMatrix] del domain. Cualquier cambio en autorización pasa
 * por ese mapa (que tiene su propio test exhaustivo).
 */
@Service
class AuthorizationService : AuthorizationUseCase {

    override fun require(principal: Principal, permission: Permission) {
        if (!has(principal, permission)) {
            throw AuthorizationDeniedException(
                principal = principal,
                permission = permission,
                reason = "roles ${principal.roles} do not include any of ${rolesGranting(permission)}",
            )
        }
    }

    override fun has(principal: Principal, permission: Permission): Boolean =
        AuthorizationMatrix.allows(principal.roles, permission)

    override fun rolesGranting(permission: Permission): Set<Role> =
        AuthorizationMatrix.rolesGranting(permission)
}
