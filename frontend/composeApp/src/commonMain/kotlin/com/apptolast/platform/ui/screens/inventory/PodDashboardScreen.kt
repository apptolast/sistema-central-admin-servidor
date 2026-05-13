package com.apptolast.platform.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apptolast.platform.ui.data.PodDto
import com.apptolast.platform.ui.navigation.AppNavigator
import com.apptolast.platform.ui.navigation.Route
import com.apptolast.platform.ui.theme.AppToLastColors

/**
 * Pantalla 1: Pod Dashboard.
 *
 * Spec: docs/design/specs/01-pod-dashboard.md
 *   - Lista de los 126 pods del cluster
 *   - Filtros: namespace, phase, label selector
 *   - Tabla virtualized (LazyColumn → react-window equivalent)
 *   - Click en pod → drawer detail (Pantalla 2)
 *
 * Phase 1: stub funcional con datos mock. Conexión real al backend cuando
 * el platform-app esté desplegado en el cluster.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodDashboardScreen(navigator: AppNavigator) {
    var searchQuery by remember { mutableStateOf("") }
    var namespaceFilter by remember { mutableStateOf<String?>(null) }

    // TODO(phase-1): conectar con InventoryClient.listPods(). Por ahora mocks.
    val pods = remember { mockPods() }
    val filtered = remember(pods, searchQuery, namespaceFilter) {
        pods.filter { pod ->
            (namespaceFilter == null || pod.namespace == namespaceFilter) &&
                (searchQuery.isBlank() || pod.name.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pods · ${filtered.size} de ${pods.size}") },
                actions = {
                    IconButton(onClick = { /* TODO: refresh */ }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refrescar")
                    }
                    IconButton(onClick = { /* TODO: open filter sheet */ }) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filtros")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar pod, namespace, image…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { pod ->
                    PodRow(pod = pod, onClick = {
                        navigator.navigateTo(Route.PodDetail(pod.namespace, pod.name))
                    })
                }
            }
        }
    }
}

@Composable
private fun PodRow(pod: PodDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhaseBadge(pod.phase)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pod.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${pod.namespace}  ·  ${pod.containers.size} container${if (pod.containers.size == 1) "" else "s"}" +
                        "  ·  ${pod.restarts} restart${if (pod.restarts == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                pod.nodeName ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhaseBadge(phase: String) {
    val color = when (phase) {
        "RUNNING" -> AppToLastColors.PodRunning
        "PENDING" -> AppToLastColors.PodPending
        "FAILED" -> AppToLastColors.PodFailed
        "SUCCEEDED" -> AppToLastColors.PodSucceeded
        else -> AppToLastColors.PodUnknown
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape),
    )
}

private fun mockPods(): List<PodDto> = listOf(
    PodDto(
        namespace = "apptolast-greenhouse-admin-dev",
        name = "greenhouse-admin-5cd99cf846-2tzz5",
        phase = "RUNNING",
        nodeName = "apptolastserver",
        podIp = "10.244.198.150",
        containers = listOf(
            PodDto.ContainerDto("greenhouse-admin", "ghcr.io/apptolast/greenhouse-admin:latest", true, 0, "RUNNING"),
        ),
        restarts = 0,
        ready = true,
        observedAt = "2026-05-13T17:00:00Z",
    ),
    PodDto(
        namespace = "apptolast-invernadero-api",
        name = "emqx-0",
        phase = "RUNNING",
        nodeName = "apptolastserver",
        podIp = "10.244.198.220",
        containers = listOf(
            PodDto.ContainerDto("emqx", "emqx/emqx:5.8", true, 0, "RUNNING"),
        ),
        restarts = 0,
        ready = true,
        observedAt = "2026-05-13T17:00:00Z",
    ),
    PodDto(
        namespace = "apptolast-invernadero-api",
        name = "timescaledb-0",
        phase = "RUNNING",
        nodeName = "apptolastserver",
        containers = listOf(
            PodDto.ContainerDto("timescaledb", "timescale/timescaledb:2.17.2-pg16", true, 0, "RUNNING"),
        ),
        restarts = 0,
        ready = true,
        observedAt = "2026-05-13T17:00:00Z",
    ),
    PodDto(
        namespace = "n8n",
        name = "n8n-prod-7ccbf86c8d-4xnmt",
        phase = "RUNNING",
        nodeName = "apptolastserver",
        containers = listOf(
            PodDto.ContainerDto("n8n", "docker.n8n.io/n8nio/n8n:1.122.4", true, 0, "RUNNING"),
        ),
        restarts = 0,
        ready = true,
        observedAt = "2026-05-13T17:00:00Z",
    ),
    PodDto(
        namespace = "cluster-ops",
        name = "homepage-847b4b96d7-47s4t",
        phase = "RUNNING",
        nodeName = "apptolastserver",
        containers = listOf(
            PodDto.ContainerDto("homepage", "ghcr.io/gethomepage/homepage:v1.3.1", true, 0, "RUNNING"),
        ),
        restarts = 0,
        ready = true,
        observedAt = "2026-05-13T17:00:00Z",
    ),
)
