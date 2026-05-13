package com.apptolast.platform.knowledge.infrastructure

import com.apptolast.platform.knowledge.application.port.inbound.QueryKnowledgePort
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Wire del cliente HTTP a rag-query.
 *
 * Properties prefix: `rag.knowledge.*`. Defaults seguros para dev y prod
 * single-node (servicio interno `rag-query.platform.svc:8082`).
 */
@Configuration
@EnableConfigurationProperties(KnowledgeProperties::class)
class KnowledgeConfig {

    @Bean
    fun knowledgeRestClient(properties: KnowledgeProperties): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(factory)
            .build()
    }

    @Bean
    fun queryKnowledgePort(
        knowledgeRestClient: RestClient,
        properties: KnowledgeProperties,
    ): QueryKnowledgePort = RestKnowledgeClient(knowledgeRestClient, properties)
}
