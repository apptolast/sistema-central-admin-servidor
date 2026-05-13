package com.apptolast.platform.secrets.application.port.outbound

import com.apptolast.platform.secrets.domain.model.OwnerRef
import com.apptolast.platform.secrets.domain.model.Secret
import com.apptolast.platform.secrets.domain.model.SecretId

/**
 * Adaptador para la API REST de Passbolt.
 *
 * Implementación: `infrastructure.passbolt.PassboltApiClient`.
 *
 * Reason: el platform-app NUNCA descifra secrets — sólo lista metadata. El
 * usuario clickea "Ver" → redirección a Passbolt UI con SSO compartido
 * (Keycloak), donde Passbolt descifra con la GPG key del usuario.
 *
 * No se incluye un método `getPlaintext` en este puerto a propósito.
 */
interface PassboltClient {
    fun listSecretsMetadata(forUserSub: String): List<Secret>
    fun findById(id: SecretId): Secret?

    /** URL de Passbolt UI donde el usuario puede descifrar el secret. */
    fun deepLinkForSecret(id: SecretId): String

    /** Verifica que el usuario tiene acceso al secret (sin descifrarlo). */
    fun canAccess(id: SecretId, userSub: String): Boolean

    fun listOwners(): List<OwnerRef>
}
