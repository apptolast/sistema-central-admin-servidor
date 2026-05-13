// platform/secrets — bounded context Secrets (Phase 4).
//
// Frontend para Passbolt — el platform-app NO almacena secrets propios, sólo
// metadata + audit log. La cifra/descifra ocurre en Passbolt (GPG end-to-end).

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springDependencyMgmt)
}

// Mismo pin que knowledge/inventory: spring-modulith 2.0.1 arrastra
// spring-core 7.0.2; RestClient.builder() de Spring Web 6.2.x explota con
// NoClassDefFoundError: MimeType$SpecificityComparator. Pin a 6.2.9 hasta
// migración a Spring Boot 4.0 (Fase 6 hardening + Java 25).
configurations.all {
    resolutionStrategy {
        force("org.springframework:spring-core:6.2.9")
        force("org.springframework:spring-test:6.2.9")
        force("org.springframework:spring-web:6.2.9")
    }
}

dependencies {
    implementation(libs.bundles.spring.base)
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.kotlin.reflect)

    runtimeOnly(libs.flyway.postgresql)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly("com.h2database:h2:2.3.232")
}
