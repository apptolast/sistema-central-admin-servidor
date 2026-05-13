package com.apptolast.platform.ui.screens.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apptolast.platform.ui.navigation.AppNavigator
import com.apptolast.platform.ui.navigation.Route

/**
 * Pantalla 2: Pod Detail.
 *
 * Spec: docs/design/specs/02-pod-detail.md
 *   - Header: namespace/name, ready, phase, restart count
 *   - Container list con image, ready, state
 *   - Env vars (REDACTED si secret), resources requests/limits
 *   - Log tail placeholder
 *   - Eventos K8s recientes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodDetailScreen(route: Route.PodDetail, navigator: AppNavigator) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(route.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            route.namespace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.goBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Section("Containers") }
            items(listOf("nginx:1.27" to true, "log-sidecar:2.4" to true)) { (image, ready) ->
                ContainerRow(image, ready)
            }

            item { Section("Resources") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Requests · CPU 300m · Memory 512Mi")
                        Text("Limits   · CPU 1.0  · Memory 1Gi")
                    }
                }
            }

            item { Section("Events (último hora)") }
            items(
                listOf(
                    "17:42 · Started container nginx",
                    "17:42 · Created pod sandbox",
                    "17:41 · Successfully assigned default/pod-1 to apptolastserver",
                ),
            ) { event ->
                Text(event, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ContainerRow(image: String, ready: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(image, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (ready) "Ready" else "Not ready",
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
