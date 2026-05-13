package com.apptolast.platform.secrets.infrastructure.passbolt

import com.apptolast.platform.secrets.application.port.outbound.PassboltClient
import com.apptolast.platform.secrets.domain.model.OwnerRef
import com.apptolast.platform.secrets.domain.model.Secret
import com.apptolast.platform.secrets.domain.model.SecretId
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Adaptador HTTP real al API de Passbolt (Wave-D D3 scaffold).
 *
 * **Estado:** scaffold listo, llamadas reales devuelven listas vacías hasta
 * que se acuerde el shape exacto del payload Passbolt en Wave-D D4-D6
 * (requiere Passbolt vivo + token de testing).
 *
 * Anti-hallucination:
 *  - Errores HTTP, parse, timeout, 401 → emptyList / null / false.
 *    NUNCA inventamos un Secret que no existe en Passbolt real.
 *  - El llamador (SecretsInventoryService) trata "no encontrado" igual que
 *    "service caído" — ambos resultan en inventario sin esa entry.
 */
class PassboltApiClient(
    private val restClient: RestClient,
    @Suppress("unused") private val properties: PassboltProperties,
) : PassboltClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun listSecretsMetadata(forUserSub: String): List<Secret> {
        return try {
            // TODO Wave-D D4: replace stub when Passbolt API contract confirmed.
            // El endpoint nativo es `/resources.json?api-version=v2`. Necesita
            // GPG-signed auth flow (no JWT simple) — ver docs/services/passbolt.md
            // por crear. Por ahora devolvemos lista vacía sin tocar red.
            log.debug("listSecretsMetadata for {} — stub (D3)", forUserSub)
            emptyList()
        } catch (ex: RestClientException) {
            log.warn("passbolt unreachable, returning empty: {}", ex.message)
            emptyList()
        }
    }

    override fun findById(id: SecretId): Secret? {
        log.debug("findById {} — stub (D3)", id)
        return null
    }

    override fun deepLinkForSecret(id: SecretId): String =
        "${properties.url.trimEnd('/')}/app/passwords/view/${id.value}"

    override fun canAccess(id: SecretId, userSub: String): Boolean {
        // Conservador: hasta que el flow de auth esté implementado, NO
        // afirmamos que ningún user tiene acceso. La UI mostrará el secret
        // como locked y el usuario hace click → redirect a Passbolt nativo.
        log.debug("canAccess {} by {} — stub (D3) returning false", id, userSub)
        return false
    }

    override fun listOwners(): List<OwnerRef> = emptyList()
}
