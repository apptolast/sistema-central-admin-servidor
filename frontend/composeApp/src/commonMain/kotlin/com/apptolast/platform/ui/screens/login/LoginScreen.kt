package com.apptolast.platform.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Pantalla 6: Login (Keycloak OIDC).
 *
 * Spec: docs/design/specs/06-login-keycloak.md
 *   - Botón "Iniciar sesión con Keycloak" → redirect a /oauth2/authorization/keycloak
 *   - Tras login muestra badge de role (admin, viewer, oncall)
 *
 * Phase 4 dependency: necesita Keycloak desplegado + Spring Security OAuth2
 * Resource Server en el platform-app.
 */

/**
 * Redirección al endpoint estándar de Spring Security OAuth2.
 * `expect` multiplatform — implementación de wasmJs hace
 * `window.location.href = "/oauth2/authorization/keycloak"`.
 */
expect fun openOidcLogin()

@Composable
fun LoginScreen(onLoginClicked: () -> Unit = { openOidcLogin() }) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(420.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "AppToLast IDP",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Internal Developer Platform",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onLoginClicked,
                    modifier = Modifier.size(width = 280.dp, height = 48.dp),
                ) {
                    Text("Iniciar sesión con Keycloak")
                }
                Text(
                    "Phase 4 dependency. Mientras tanto, todas las rutas son " +
                        "públicas detrás de cluster-ops basicauth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
