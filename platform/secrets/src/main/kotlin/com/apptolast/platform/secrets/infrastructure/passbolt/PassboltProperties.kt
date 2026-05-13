package com.apptolast.platform.secrets.infrastructure.passbolt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Config del cliente Passbolt. Properties prefix: `secrets.passbolt.*`.
 *
 * Cuando `url` está blank (default), el módulo arranca sin backend
 * conectado (StubPassboltClient) — el inventario aparece vacío. Esto
 * permite que el monolito siga arrancando en dev sin Passbolt desplegado.
 */
@ConfigurationProperties(prefix = "secrets.passbolt")
data class PassboltProperties(
    val url: String = "",
    /**
     * Token API que Passbolt acepta. Inyectado via Secret K8s — NUNCA en
     * values.yaml plano. El backend solo necesita listar metadata (no
     * descifrar), así que es un token de read-only ideal para Wave-D.
     */
    val apiToken: String = "",
    val connectTimeout: Duration = Duration.ofMillis(500),
    val readTimeout: Duration = Duration.ofSeconds(2),
) {
    /** True si el adapter real puede usarse (URL configured). */
    val configured: Boolean get() = url.isNotBlank()
}
