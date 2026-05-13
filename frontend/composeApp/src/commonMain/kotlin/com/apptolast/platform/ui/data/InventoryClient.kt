package com.apptolast.platform.ui.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente Ktor para la API del platform-app.
 *
 * Endpoint: /api/v1/inventory/[recurso]. URL base resuelta a runtime (dev: localhost,
 * prod: idp.apptolast.com vía Traefik IngressRoute).
 */
class InventoryClient(private val baseUrl: String) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    suspend fun listPods(namespace: String? = null, phase: String? = null): List<PodDto> =
        client.get("$baseUrl/api/v1/inventory/pods") {
            namespace?.let { parameter("namespace", it) }
            phase?.let { parameter("phase", it) }
        }.body()

    /**
     * Devuelve el pod básico (compat). Si llamadores nuevos quieren runbooks,
     * usar [getPodDetail].
     */
    suspend fun getPod(namespace: String, name: String): PodDto? =
        getPodDetail(namespace, name)?.pod

    /**
     * Pod + runbooks relevantes (vía knowledge module en el backend).
     *
     * Devuelve `null` si el pod no existe (404) o si la llamada falla. NUNCA
     * lanza — anti-hallucination: prefer empty UI vs crash inesperado.
     */
    suspend fun getPodDetail(namespace: String, name: String): PodDetailDto? {
        return try {
            val response: HttpResponse =
                client.get("$baseUrl/api/v1/inventory/pods/$namespace/$name")
            val status = response.status
            when {
                status == HttpStatusCode.NotFound -> null
                status.value in 200..299 -> response.body<PodDetailDto>()
                else -> null
            }
        } catch (ex: Throwable) {
            null
        }
    }

    suspend fun listServices(): List<ServiceDto> =
        client.get("$baseUrl/api/v1/inventory/services").body()

    suspend fun listExpiringCertificates(thresholdDays: Int = 30): List<CertificateDto> =
        client.get("$baseUrl/api/v1/inventory/certificates/expiring") {
            parameter("thresholdDays", thresholdDays)
        }.body()
}
