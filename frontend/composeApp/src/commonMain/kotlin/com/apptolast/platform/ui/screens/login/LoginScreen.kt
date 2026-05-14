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
 *   - Botón "Iniciar sesión con Keycloak" → redirect a Keycloak OIDC
 *   - Tras login muestra badge de role (admin, viewer, oncall)
 *
 * Keycloak ya está desplegado; el backend aún no fuerza sesión/RBAC.
 */

/**
 * Redirección al authorization endpoint de Keycloak.
 * `expect` multiplatform — implementación de wasmJs hace
 * `window.location.href = "https://auth.apptolast.com/realms/apptolast/..."`.
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
                    "Keycloak activo en auth.apptolast.com. RBAC de backend pendiente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
