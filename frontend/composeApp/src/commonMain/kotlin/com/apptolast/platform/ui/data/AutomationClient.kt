package com.apptolast.platform.ui.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente Ktor para la API de Automation.
 *
 * Endpoints:
 *  - GET  /api/v1/automation/audit       — paginado, filtros opcionales
 *  - GET  /api/v1/automation/audit/{id}  — detalle full
 *  - POST /api/v1/automation/run         — ejecutar SafeCommand
 *
 * Anti-hallucination: en errores HTTP, devuelve estados conservadores
 * (página vacía, null detail, throw para run() ya que el caller necesita
 * feedback claro de si ejecutó o no).
 */
class AutomationClient(baseUrl: String = "") {

    private val apiBaseUrl = baseUrl.trimEnd('/')

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                classDiscriminator = "kind"
            })
        }
    }

    suspend fun listAudit(
        page: Int = 0,
        size: Int = 50,
        commandKind: String? = null,
        outcome: String? = null,
    ): AuditPageDto = try {
        client.get("$apiBaseUrl/api/v1/automation/audit") {
            parameter("page", page)
            parameter("size", size)
            commandKind?.let { parameter("commandKind", it) }
            outcome?.let { parameter("outcome", it) }
        }.body()
    } catch (_: Throwable) {
        AuditPageDto(page = page, size = size, items = emptyList())
    }

    suspend fun getAuditEntry(id: String): AuditEntryDto? = try {
        val response: HttpResponse = client.get("$apiBaseUrl/api/v1/automation/audit/$id")
        if (response.status.value in 200..299) response.body<AuditEntryDto>() else null
    } catch (_: Throwable) {
        null
    }

    /**
     * Ejecuta un comando. A diferencia de las queries, esto sí propaga
     * excepciones porque la UI debe distinguir "no ejecutado" de "no se sabe".
     */
    suspend fun run(request: AutomationRunRequest): ExecutionOutcomeDto =
        client.post("$apiBaseUrl/api/v1/automation/run") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun listCronJobs(namespace: String? = null): List<CronJobDto> = try {
        client.get("$apiBaseUrl/api/v1/automation/cronjobs") {
            namespace?.let { parameter("namespace", it) }
        }.body()
    } catch (_: Throwable) {
        emptyList()
    }
}
