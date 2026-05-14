package com.apptolast.platform.ui.screens.cronjobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apptolast.platform.ui.data.AutomationClient
import com.apptolast.platform.ui.data.AutomationRunRequest
import com.apptolast.platform.ui.data.CronJobDto
import com.apptolast.platform.ui.navigation.AppNavigator
import com.apptolast.platform.ui.navigation.Route
import kotlinx.coroutines.launch

/**
 * Pantalla 5: Cronjob Board (Wave-E E4).
 *
 * Renderiza CronJobs reales desde GET /api/v1/automation/cronjobs. También:
 *  - El botón "trigger ahora" hace POST /api/v1/automation/run con
 *    SafeCommand.TriggerCronJob. Resultado mostrado en Snackbar.
 *  - El icono "ver audit" navega a la pantalla AuditLog (Route.AuditLog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronjobBoardScreen(
    navigator: AppNavigator,
    client: AutomationClient = remember { AutomationClient() },
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var cronjobs by remember { mutableStateOf<List<CronJobDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        cronjobs = client.listCronJobs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cronjobs · ${cronjobs.size}") },
                actions = {
                    IconButton(onClick = { navigator.navigateTo(Route.AuditLog) }) {
                        Icon(Icons.Outlined.History, contentDescription = "Audit log")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cronjobs) { cj ->
                CronjobCard(cj, onTrigger = {
                    scope.launch {
                        try {
                            val outcome = client.run(
                                AutomationRunRequest.TriggerCronJobDto(
                                    namespace = cj.namespace,
                                    cronJobName = cj.name,
                                ),
                            )
                            snackbar.showSnackbar(
                                if (outcome.exitCode == 0) "${cj.name}: disparado (${outcome.durationMs}ms)"
                                else "${cj.name}: exit ${outcome.exitCode}",
                            )
                        } catch (ex: Throwable) {
                            // Anti-hallucination: no inventes que fue exitoso.
                            // Mostrar el mensaje crudo del error.
                            snackbar.showSnackbar("${cj.name}: ${ex.message ?: "error"}")
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun CronjobCard(cj: CronJobDto, onTrigger: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cj.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onTrigger) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Ejecutar ahora")
                }
            }
            Text(cj.namespace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(cj.schedule, style = MaterialTheme.typography.bodyMedium)
            Text("Estado: ${cj.status} · activos: ${cj.activeJobs}", style = MaterialTheme.typography.bodySmall)
            cj.lastScheduleTime?.let { Text("Último schedule: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
