package com.apptolast.platform.ui.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente Ktor para la API del platform-app.
 *
 * Endpoint: `/api/v1/inventory/*`. URL base resuelta a runtime (dev: localhost,
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

    suspend fun getPod(namespace: String, name: String): PodDto? =
        client.get("$baseUrl/api/v1/inventory/pods/$namespace/$name").body()

    suspend fun listServices(): List<ServiceDto> =
        client.get("$baseUrl/api/v1/inventory/services").body()

    suspend fun listExpiringCertificates(thresholdDays: Int = 30): List<CertificateDto> =
        client.get("$baseUrl/api/v1/inventory/certificates/expiring") {
            parameter("thresholdDays", thresholdDays)
        }.body()
}
