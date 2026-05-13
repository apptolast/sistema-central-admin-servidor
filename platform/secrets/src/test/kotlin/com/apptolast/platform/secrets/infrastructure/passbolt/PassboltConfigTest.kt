package com.apptolast.platform.secrets.infrastructure.passbolt

import com.apptolast.platform.secrets.domain.model.SecretId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Tests del selector entre PassboltApiClient (real) y StubPassboltClient
 * (fallback) según la presencia de URL configurada.
 *
 * Wave-D D3 — el adapter real solo se exercita en D4-D6 cuando Passbolt
 * esté desplegado. Este test verifica que la rama "no configured" del
 * config produce el stub y que el stub honora el contrato anti-hallucination.
 */
class PassboltConfigTest {

    @Test
    fun `properties without url yield not-configured`() {
        val props = PassboltProperties(url = "")
        props.configured shouldBe false
    }

    @Test
    fun `properties with url yield configured`() {
        val props = PassboltProperties(url = "https://passbolt.apptolast.com")
        props.configured shouldBe true
    }

    @Test
    fun `default timeouts are aggressive (sub second connect)`() {
        val props = PassboltProperties(url = "https://x")
        props.connectTimeout shouldBe Duration.ofMillis(500)
        props.readTimeout shouldBe Duration.ofSeconds(2)
    }

    @Test
    fun `StubPassboltClient never returns secrets — anti-hallucination`() {
        val stub = StubPassboltClient()

        stub.listSecretsMetadata("anyone").shouldBeEmpty()
        stub.findById(SecretId("any-id")) shouldBe null
        stub.canAccess(SecretId("any-id"), "anyone") shouldBe false
        stub.listOwners().shouldBeEmpty()
    }

    @Test
    fun `StubPassboltClient deepLink returns unavailable URL`() {
        val stub = StubPassboltClient()
        stub.deepLinkForSecret(SecretId("foo")).shouldContain("unavailable")
    }

    @Test
    fun `passboltClient bean is Stub when url blank`() {
        val config = PassboltConfig()
        val client = config.passboltClient(
            PassboltProperties(url = ""),
            config.passboltRestClient(PassboltProperties(url = "")),
        )
        client.shouldBeInstanceOf<StubPassboltClient>()
    }

    @Test
    fun `passboltClient bean is ApiClient when url configured`() {
        val config = PassboltConfig()
        val props = PassboltProperties(url = "https://passbolt.apptolast.com")
        val client = config.passboltClient(props, config.passboltRestClient(props))
        client.shouldBeInstanceOf<PassboltApiClient>()
    }

    @Test
    fun `PassboltApiClient deepLink formats correctly`() {
        val props = PassboltProperties(url = "https://passbolt.apptolast.com/")
        val client = PassboltApiClient(
            PassboltConfig().passboltRestClient(props),
            props,
        )
        client.deepLinkForSecret(SecretId("abc-123"))
            .shouldContain("/app/passwords/view/abc-123")
    }

    @Test
    fun `PassboltApiClient list returns empty in scaffold mode (no Passbolt real)`() {
        // Wave-D D3: el adapter es scaffold — devuelve empty hasta D4-D6 wire real.
        val props = PassboltProperties(url = "https://passbolt.example")
        val client = PassboltApiClient(
            PassboltConfig().passboltRestClient(props),
            props,
        )
        client.listSecretsMetadata("user-x").shouldBeEmpty()
        client.findById(SecretId("foo")) shouldBe null
    }
}
