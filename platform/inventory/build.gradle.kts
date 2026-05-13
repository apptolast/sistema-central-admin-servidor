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
