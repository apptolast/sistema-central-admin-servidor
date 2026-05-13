package com.apptolast.platform.secrets.infrastructure.passbolt

import com.apptolast.platform.secrets.application.port.outbound.PassboltClient
import com.apptolast.platform.secrets.domain.model.OwnerRef
import com.apptolast.platform.secrets.domain.model.Secret
import com.apptolast.platform.secrets.domain.model.SecretId
import org.slf4j.LoggerFactory

/**
 * Stub que activa cuando Passbolt no está configurado (URL blank).
 *
 * Devuelve listas vacías y rechaza access checks. Permite que el monolito
 * arranque en dev sin Passbolt vivo. Wave-D D4 reemplazará esto por
 * PassboltApiClient real cuando el servicio esté desplegado.
 *
 * NO inventa secrets — anti-hallucination: el inventario vacío es mejor
 * que un inventario con datos falsos.
 */
class StubPassboltClient : PassboltClient {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.warn(
            "StubPassboltClient activo. secrets.passbolt.url no configured. " +
                "El inventario de secrets aparecerá vacío.",
        )
    }

    override fun listSecretsMetadata(forUserSub: String): List<Secret> = emptyList()
    override fun findById(id: SecretId): Secret? = null
    override fun deepLinkForSecret(id: SecretId): String = "https://passbolt.unavailable"
    override fun canAccess(id: SecretId, userSub: String): Boolean = false
    override fun listOwners(): List<OwnerRef> = emptyList()
}
