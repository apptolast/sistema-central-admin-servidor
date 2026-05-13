package com.apptolast.platform.ui.screens.login

import kotlinx.browser.window

/**
 * `actual` para [openOidcLogin] en wasmJs.
 *
 * Redirige a `/oauth2/authorization/keycloak` — el endpoint default que
 * Spring Security OAuth2 Client expone para iniciar el authorization code
 * flow contra el provider `keycloak` configurado en application.yml.
 *
 * Se hace `window.location.href = ...` en vez de un fetch, porque OAuth2
 * code flow requiere navegación REAL del browser (no XHR) para que la
 * cookie de sesión se establezca correctamente tras el callback.
 */
actual fun openOidcLogin() {
    window.location.href = "/oauth2/authorization/keycloak"
}
