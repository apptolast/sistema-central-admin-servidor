package com.apptolast.platform.automation.infrastructure

import com.apptolast.platform.automation.application.port.outbound.CommandExecutor
import com.apptolast.platform.automation.application.service.SafeOpsKernel
import com.apptolast.platform.automation.domain.model.Whitelist
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@ConditionalOnProperty(prefix = "automation", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class AutomationConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @Profile("!test")
    fun kubernetesClient(): KubernetesClient = KubernetesClientBuilder().build()

    @Bean
    @ConditionalOnMissingBean
    fun safeOpsWhitelist(props: AutomationProperties): Whitelist =
        Whitelist(
            allowedKinds = props.allowedKinds.ifEmpty { Whitelist.READ_ONLY.allowedKinds },
            allowedNamespaces = props.allowedNamespaces,
        )

    @Bean
    @ConditionalOnMissingBean
    fun commandExecutor(client: KubernetesClient): CommandExecutor = Fabric8CommandExecutor(client)

    @Bean
    fun safeOpsKernel(whitelist: Whitelist, executor: CommandExecutor): SafeOpsKernel =
        SafeOpsKernel(whitelist, executor)
}

@ConfigurationProperties(prefix = "automation")
data class AutomationProperties(
    val enabled: Boolean = true,
    val allowedKinds: Set<String> = emptySet(),
    val allowedNamespaces: Set<String> = emptySet(),
)
