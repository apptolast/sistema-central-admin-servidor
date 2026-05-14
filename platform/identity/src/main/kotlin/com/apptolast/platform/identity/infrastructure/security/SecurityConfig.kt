package com.apptolast.platform.identity.infrastructure.security

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Spring Security Resource Server config (Wave-D D2).
 *
 * Si `identity.oidc.enabled=false` (default dev): cadena permitAll — el
 * monolito arranca SIN Keycloak. Esto preserva el flow de dev local.
 *
 * Si `identity.oidc.enabled=true`: cadena JWT obligatoria. Endpoints
 * /actuator/health/[*] y /actuator/info siempre permitAll (probes K8s
 * no llevan token). Resto = authenticated.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(IdentityProperties::class)
class SecurityConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun filterChain(http: HttpSecurity, props: IdentityProperties): SecurityFilterChain {
        // CSRF off para una API REST stateless. La defense es: JWT en
        // Authorization header (Bearer) — no cookies cross-site exploitables.
        http.csrf { it.disable() }
        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

        http.authorizeHttpRequests { auth ->
            auth.requestMatchers(
                "/actuator/health/**",
                "/actuator/info",
                "/actuator/prometheus",
            ).permitAll()
            if (props.enabled) {
                auth.anyRequest().authenticated()
            } else {
                log.warn(
                    "SecurityConfig: identity.oidc.enabled=false — API en permitAll. " +
                        "Activar identity.oidc.enabled=true en producción.",
                )
                auth.anyRequest().permitAll()
            }
        }

        if (props.enabled) {
            require(props.issuerUri.isNotBlank()) {
                "identity.oidc.enabled=true requires identity.oidc.issuer-uri to be set"
            }
            http.oauth2ResourceServer { rs ->
                rs.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(KeycloakJwtConverter(props))
                }
            }
        }
        return http.build()
    }
}

/**
 * Converter Spring Security: JWT → Authentication con authorities
 * `ROLE_<NAME>` derivadas del claim `realm_access.roles`. Mantiene también
 * la traducción a [com.apptolast.platform.identity.api.Principal] vía
 * [JwtPrincipalMapper] para que los módulos consumer puedan obtenerlo del
 * `Authentication.details`.
 */
class KeycloakJwtConverter(
    private val props: IdentityProperties,
) : Converter<Jwt, AbstractAuthenticationToken> {

    private val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
        // Spring Security default mira `scope` y `scp`. Aquí lo overrideamos
        // a "realm_access.roles" via callback en convert().
    }

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val principal = JwtPrincipalMapper.fromJwt(jwt, props.realmRolesClaim)
        val authorities: Collection<GrantedAuthority> = principal.roles
            .map { SimpleGrantedAuthority("ROLE_${it.name}") } +
            (authoritiesConverter.convert(jwt) ?: emptyList())
        val token = JwtAuthenticationToken(jwt, authorities, principal.userSub)
        token.details = principal
        return token
    }
}
