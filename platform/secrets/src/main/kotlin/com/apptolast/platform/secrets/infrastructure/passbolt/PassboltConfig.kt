package com.apptolast.platform.secrets.infrastructure.passbolt

import com.apptolast.platform.secrets.application.port.outbound.PassboltClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Wire del cliente Passbolt al monolito (Wave-D D3).
 *
 * Selector: si `secrets.passbolt.url` está blank → bean = StubPassboltClient
 * (warn al log, inventario vacío). Si no blank → PassboltApiClient con
 * RestClient construido con los timeouts agresivos de [PassboltProperties].
 *
 * Patrón idéntico al usado en knowledge/KnowledgeConfig.kt (commit fab4acc).
 */
@Configuration
@EnableConfigurationProperties(PassboltProperties::class)
class PassboltConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun passboltRestClient(properties: PassboltProperties): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        val baseUrl = if (properties.configured) properties.url else "http://passbolt.unavailable"
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build()
    }

    @Bean
    fun passboltClient(
        properties: PassboltProperties,
        passboltRestClient: RestClient,
    ): PassboltClient {
        return if (properties.configured) {
            log.info("Passbolt configured at {} — using PassboltApiClient", properties.url)
            PassboltApiClient(passboltRestClient, properties)
        } else {
            log.warn("Passbolt not configured (secrets.passbolt.url blank) — using StubPassboltClient")
            StubPassboltClient()
        }
    }
}
