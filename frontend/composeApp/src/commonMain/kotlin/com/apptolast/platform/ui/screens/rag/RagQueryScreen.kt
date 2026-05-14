package com.apptolast.platform.ui.screens.rag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apptolast.platform.ui.data.RagAnswer
import com.apptolast.platform.ui.data.RagCitation
import com.apptolast.platform.ui.data.RagClient
import kotlinx.coroutines.launch

/**
 * Pantalla 4: RAG Query UI — wired al microservicio rag-query.
 *
 * Anti-hallucination:
 *   - Cada respuesta cited muestra los chips de citation reales del backend.
 *   - Si no hay evidencia (LOW_NO_EVIDENCE o fallo de red), se muestra el
 *     mensaje canónico "No encuentro evidencia documentada" — NUNCA inventa
 *     una respuesta.
 *
 * baseUrl: vacío por defecto para same-origin. En producción Traefik enruta
 * /api/v1/rag/[recurso] al servicio rag-query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagQueryScreen(
    client: RagClient = remember { RagClient() },
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var conversation by remember {
        mutableStateOf<List<RagMessage>>(
            listOf(
                RagMessage.Assistant(
                    text = "Hola. Pregúntame sobre el cluster, los runbooks o cualquier sistema desplegado.",
                    citations = emptyList(),
                ),
            ),
        )
    }
    val scope = rememberCoroutineScope()

    fun send() {
        val q = query.trim()
        if (q.isBlank() || loading) return
        conversation = conversation + RagMessage.User(q)
        query = ""
        loading = true
        scope.launch {
            val answer = client.ask(q)
            val reply = when (answer) {
                is RagAnswer.Cited -> RagMessage.Assistant(
                    text = answer.body,
                    citations = answer.citations,
                )
                RagAnswer.NoEvidence -> RagMessage.Assistant(
                    text = RagAnswer.NoEvidence.MESSAGE,
                    citations = emptyList(),
                )
            }
            conversation = conversation + reply
            loading = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pregunta a la plataforma") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(conversation) { msg -> MessageRow(msg) }
                if (loading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("¿Por qué falló el último despliegue de inventory?") },
                    modifier = Modifier.weight(1f),
                    enabled = !loading,
                )
                IconButton(onClick = ::send, enabled = !loading && query.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}

private sealed interface RagMessage {
    val text: String

    data class User(override val text: String) : RagMessage
    data class Assistant(override val text: String, val citations: List<RagCitation>) : RagMessage
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageRow(msg: RagMessage) {
    when (msg) {
        is RagMessage.User -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                msg.text,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        is RagMessage.Assistant -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(msg.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (msg.citations.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        msg.citations.forEach { c ->
                            AssistChip(
                                onClick = { /* citation chip visible para copy manual */ },
                                label = { Text("${c.sourcePath.substringAfterLast('/')}#${c.section}") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
