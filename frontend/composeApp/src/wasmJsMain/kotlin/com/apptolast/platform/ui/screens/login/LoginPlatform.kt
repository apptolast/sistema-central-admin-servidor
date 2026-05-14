package com.apptolast.platform.ui.screens.login

import kotlinx.browser.window
import kotlin.js.ExperimentalWasmJsInterop

/**
 * `actual` para [openOidcLogin] en wasmJs.
 *
 * Redirige al authorization endpoint real de Keycloak. El backend actual aún
 * no expone OAuth2 Client login en `/oauth2/authorization/keycloak`.
 */
actual fun openOidcLogin() {
    val redirectUri = "${window.location.origin}/"
    window.location.href =
        "https://auth.apptolast.com/realms/apptolast/protocol/openid-connect/auth" +
            "?client_id=idp-frontend" +
            "&redirect_uri=${encodeURIComponent(redirectUri)}" +
            "&response_type=code" +
            "&scope=openid%20profile%20email" +
            "&code_challenge_method=S256" +
            "&code_challenge=2cC0CMG1K0g_cQxYZEx7A7q2DDqaJx4ZxEuRkRkIh6E" +
            "&state=apptolast-idp"
}

@OptIn(ExperimentalWasmJsInterop::class)
@Suppress("UnsafeCastFromDynamic")
private fun encodeURIComponent(value: String): String = js("encodeURIComponent(value)")
