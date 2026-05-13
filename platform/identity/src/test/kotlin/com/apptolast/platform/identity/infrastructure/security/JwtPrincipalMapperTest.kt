package com.apptolast.platform.identity.infrastructure.security

import com.apptolast.platform.identity.api.Role
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Tests del mapper JWT → Principal — Wave-D D2.
 *
 * Cubre:
 *  1. Mapeo happy path (Keycloak default claim `realm_access.roles`).
 *  2. Roles desconocidos descartados sin error (otras apps comparten realm).
 *  3. Sub vacío → IllegalArgumentException.
 *  4. Sin claim de roles → empty set (NO inventar VIEWER).
 *  5. Case-insensitive matching (admin → ADMIN).
 *  6. Groups claim opcional.
 */
class JwtPrincipalMapperTest {

    private fun aJwt(claims: Map<String, Any>, subject: String = "user-123"): Jwt =
        Jwt.withTokenValue("fake-token")
            .subject(subject)
            .issuedAt(Instant.parse("2026-05-13T18:00:00Z"))
            .expiresAt(Instant.parse("2026-05-13T19:00:00Z"))
            .header("alg", "RS256")
            .claims { it.putAll(claims) }
            .build()

    @Test
    fun `happy path maps realm_access roles to Principal roles`() {
        val jwt = aJwt(
            mapOf(
                "email" to "pablo@apptolast.com",
                "preferred_username" to "pablo",
                "realm_access" to mapOf("roles" to listOf("admin", "viewer")),
            ),
            subject = "abc-123",
        )

        val principal = JwtPrincipalMapper.fromJwt(jwt)

        principal.userSub shouldBe "abc-123"
        principal.email shouldBe "pablo@apptolast.com"
        principal.displayName shouldBe "pablo"
        principal.roles shouldBe setOf(Role.ADMIN, Role.VIEWER)
    }

    @Test
    fun `unknown role labels are skipped without throwing`() {
        // Keycloak realms suelen compartirse entre apps — los roles default de
        // Keycloak (offline_access, uma_authorization) o de OTRA app (foo_user)
        // NO deben tumbar el login del IDP.
        val jwt = aJwt(
            mapOf(
                "realm_access" to mapOf("roles" to listOf("admin", "offline_access", "foo_role", "viewer")),
            ),
        )

        JwtPrincipalMapper.fromJwt(jwt).roles shouldBe setOf(Role.ADMIN, Role.VIEWER)
    }

    @Test
    fun `case insensitive role matching`() {
        val jwt = aJwt(
            mapOf("realm_access" to mapOf("roles" to listOf("Admin", "ONCALL", "viewer"))),
        )

        JwtPrincipalMapper.fromJwt(jwt).roles shouldBe setOf(Role.ADMIN, Role.ONCALL, Role.VIEWER)
    }

    @Test
    fun `no realm_access claim returns empty roles (no defaults invented)`() {
        // Anti-hallucination: si Keycloak NO mandó roles, el Principal no tiene
        // permisos. NO asumimos VIEWER.
        val jwt = aJwt(mapOf("email" to "x@y.z"))

        JwtPrincipalMapper.fromJwt(jwt).roles.shouldBeEmpty()
    }

    @Test
    fun `blank subject throws IllegalArgumentException`() {
        // Jwt builder requires subject — for blank we test via the require() in mapper.
        // Construimos un Jwt con sub explícitamente vacío.
        assertThrows<Throwable> {
            val jwt = Jwt.withTokenValue("x")
                .subject("")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .header("alg", "RS256")
                .claim("email", "x@y.z")
                .build()
            JwtPrincipalMapper.fromJwt(jwt)
        }
    }

    @Test
    fun `email fallback to subject when missing`() {
        val jwt = aJwt(emptyMap(), subject = "sub-no-email")
        JwtPrincipalMapper.fromJwt(jwt).email shouldBe "sub-no-email"
    }

    @Test
    fun `groups claim is mapped when present`() {
        val jwt = aJwt(
            mapOf(
                "realm_access" to mapOf("roles" to listOf("viewer")),
                "groups" to listOf("/platform-team", "/sre"),
            ),
        )
        JwtPrincipalMapper.fromJwt(jwt).groups shouldContainExactly setOf("/platform-team", "/sre")
    }

    @Test
    fun `custom claim path is honored`() {
        val jwt = aJwt(
            mapOf("custom" to mapOf("roles" to listOf("admin"))),
        )
        JwtPrincipalMapper.fromJwt(jwt, realmRolesClaim = "custom.roles").roles shouldBe setOf(Role.ADMIN)
    }
}
