// ============================================================================
// platform/inventory — bounded context Inventory (Fase 1).
//
// Hexagonal layering enforced by ArchUnit (ver tests/architecture):
//   api            -> public events + DTOs consumibles por otros módulos
//   domain         -> modelos puros, sin Spring/JPA
//   application    -> use cases (puertos in/out), depende SOLO de domain + api
//   infrastructure -> adapters JPA/REST/K8s, depende de application + domain
//
// Otros módulos del monolito sólo pueden importar `inventory.api.**`,
// nunca `inventory.domain.**` ni `inventory.infrastructure.**`.
// ============================================================================

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springDependencyMgmt)
}

// Mismo pin que knowledge/build.gradle.kts (commit fab4acc). Necesario aquí
// porque InventoryKnowledgeFlowE2ETest usa RestClient.builder() de spring-web
// 6.2.x; sin el force, spring-modulith 2.0.1 arrastra spring-core 7.0.2 y
// explota con NoClassDefFoundError: MimeType$SpecificityComparator.
// Quitar cuando migremos a Spring Boot 4.0.x (Fase 6 hardening + Java 25).
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

    // Cross-module: inventory enriquece PodDetail con runbooks vía knowledge.
    // Importa SÓLO contratos públicos: application.port.inbound (QueryKnowledgePort)
    // + domain.model (Citation value type). Justificado en ADR-0007.
    implementation(project(":knowledge"))

    runtimeOnly(libs.flyway.postgresql)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly("com.h2database:h2:2.3.232")
}
