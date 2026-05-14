package com.apptolast.platform.ui.screens.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apptolast.platform.ui.data.InventoryClient
import com.apptolast.platform.ui.data.PodDetailDto
import com.apptolast.platform.ui.data.RunbookCitationDto
import com.apptolast.platform.ui.data.PodDto
import com.apptolast.platform.ui.navigation.AppNavigator
import com.apptolast.platform.ui.navigation.Route

/**
 * Pantalla 2: Pod Detail.
 *
 * Wire-up vía Wave-C C4 (commit "feat(phase-3): wave-C C4"):
 * llama a `InventoryClient.getPodDetail()` y renderiza:
 *   - Header: namespace/name, ready, phase, restart count
 *   - Containers (image, ready, state, restart count)
 *   - Runbooks relacionados: SOLO si `relatedRunbooks.isNotEmpty()` —
 *     regla anti-hallucination ([[feedback_rag_anti_hallucination]]).
 *
 * El cliente está hardcoded a `http://localhost:8080` por ahora. Cuando el
 * monolito esté desplegado en el cluster, vendrá inyectado o resuelto del
 * Window.location en Wasm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodDetailScreen(
    route: Route.PodDetail,
    navigator: AppNavigator,
    client: InventoryClient = remember { InventoryClient(baseUrl = "http://localhost:8080") },
) {
    var state by remember { mutableStateOf<PodDetailUiState>(PodDetailUiState.Loading) }

    LaunchedEffect(route.namespace, route.name) {
        state = PodDetailUiState.Loading
        val detail = client.getPodDetail(route.namespace, route.name)
        state = if (detail == null) PodDetailUiState.NotFound
        else PodDetailUiState.Loaded(detail)
    }

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
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopStart,
        ) {
            when (val s = state) {
                PodDetailUiState.Loading -> LoadingView()
                PodDetailUiState.NotFound -> NotFoundView(route)
                is PodDetailUiState.Loaded -> LoadedView(s.detail)
            }
        }
    }
}

private sealed interface PodDetailUiState {
    data object Loading : PodDetailUiState
    data object NotFound : PodDetailUiState
    data class Loaded(val detail: PodDetailDto) : PodDetailUiState
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotFoundView(route: Route.PodDetail) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pod no encontrado", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${route.namespace}/${route.name} no existe o el backend está caído.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadedView(detail: PodDetailDto) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { OverviewCard(detail.pod) }

        item { Section("Containers (${detail.pod.containers.size})") }
        items(detail.pod.containers) { container ->
            ContainerRow(container)
        }

        // Anti-hallucination: SOLO renderizar runbooks si hay evidencia real.
        // Si knowledge no encontró ninguno o estaba caído, la sección no aparece.
        if (detail.relatedRunbooks.isNotEmpty()) {
            item { Section("Runbooks relacionados (${detail.relatedRunbooks.size})") }
            items(detail.relatedRunbooks) { runbook ->
                RunbookRow(runbook)
            }
        }
    }
}

@Composable
private fun OverviewCard(pod: PodDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyValue("Phase", pod.phase)
            KeyValue("Ready", if (pod.ready) "Yes" else "No")
            KeyValue("Node", pod.nodeName ?: "—")
            KeyValue("Pod IP", pod.podIp ?: "—")
            KeyValue("Restarts", pod.restarts.toString())
            pod.ownerKind?.let { kind ->
                KeyValue("Owner", "$kind/${pod.ownerName ?: "—"}")
            }
            KeyValue("Observed", pod.observedAt)
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ContainerRow(container: PodDto.ContainerDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(container.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (container.ready) "Ready" else "Not ready",
                    color = if (container.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(container.image, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${container.state} · ${container.restartCount} restart${if (container.restartCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RunbookRow(runbook: RunbookCitationDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = "Runbook",
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(runbook.sourcePath, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${runbook.section} · ${runbook.sha.take(7)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { /* clipboard handled by browser/JS interop; cita visible para copy manual */ }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar cita", modifier = Modifier.padding(end = 4.dp))
                Text("Cita")
            }
        }
    }
}
