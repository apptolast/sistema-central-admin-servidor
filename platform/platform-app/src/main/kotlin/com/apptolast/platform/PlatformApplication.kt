package com.apptolast.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

/**
 * Bootstrap del monolito modular.
 *
 * Cada bounded context vive en su propio módulo Gradle bajo platform/<module>/.
 * Spring Modulith verifica los boundaries en tests (ApplicationModules.of(...).verify()).
 */
@SpringBootApplication
@Modulithic(systemName = "apptolast-platform")
class PlatformApplication

fun main(args: Array<String>) {
    runApplication<PlatformApplication>(*args)
}
